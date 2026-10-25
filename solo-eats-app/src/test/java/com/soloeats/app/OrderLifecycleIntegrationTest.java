package com.soloeats.app;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.service.OrderApplicationService;
import com.soloeats.order.service.OrderPaymentApplicationService;
import com.soloeats.order.service.RefundService;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.order.service.result.CancelResult;
import com.soloeats.order.service.result.CreateOrderResult;
import com.soloeats.order.service.result.RefundResult;
import com.soloeats.payment.domain.enums.PaymentStatus;
import com.soloeats.payment.service.PaymentCallbackCommand;
import com.soloeats.payment.service.PaymentResult;
import com.soloeats.payment.service.PaymentService;
import com.soloeats.payment.util.PaymentSignUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单主链路集成测试。
 * 使用 Testcontainers 启动真实 MySQL，Flyway 执行 V1/V2；Redis 与 RabbitMQ 以 Mock 替代，
 * 支付成功/超时的 MQ 消费由测试直接调用应用服务模拟，验证数据库最终状态。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OrderLifecycleIntegrationTest {

    private static final String CALLBACK_SECRET = "test-callback-secret";
    private static final long USER_ID = 1L;
    private static final long MERCHANT_ID = 10L;
    private static final long SHOP_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long COUPON_ID = 3001L;

    private static final String MYSQL_IMAGE =
            System.getProperty("test.mysql.image", "mysql:8.0.46");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName("solo_eats_test")
            .withUsername("solo")
            .withPassword("test123456")
            .withEnv("TZ", "Asia/Shanghai")
            .withCommand(
                    "--default-time-zone=+08:00",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci"
            )
            .withUrlParam("serverTimezone", "Asia/Shanghai")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @MockitoBean
    private DistributedLock distributedLock;
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderApplicationService orderApplicationService;
    @Autowired
    private OrderPaymentApplicationService orderPaymentApplicationService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RefundService refundService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetState() {
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(operations);
        lenient().when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(),
                        any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());

        cleanBusinessTables();
        seedFixtures();
    }

    @Test
    @DisplayName("下单：锁定库存、核销优惠券、落订单主子表与流水")
    void createOrderLocksStockAndCoupon() {
        CreateOrderResult result = createOrder(true);

        assertThat(result.status()).isEqualTo(OrderStatus.WAIT_PAY.getCode());
        assertThat(result.goodsAmount()).isEqualTo(5600L);
        assertThat(result.packagingFee()).isEqualTo(100L);
        assertThat(result.deliveryFee()).isEqualTo(300L);
        assertThat(result.discountAmount()).isEqualTo(500L);
        assertThat(result.payAmount()).isEqualTo(5500L);

        assertThat(stock("available_qty")).isEqualTo(98);
        assertThat(stock("locked_qty")).isEqualTo(2);
        assertThat(stock("sold_qty")).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_stock_lock WHERE order_id = ?", Integer.class, result.orderId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock_log WHERE biz_id = ? AND op = 'LOCK'",
                Integer.class, result.orderId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, COUPON_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM t_user_coupon WHERE id = ?", Long.class, COUPON_ID))
                .isEqualTo(result.orderId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM t_order_discount WHERE order_id = ?", Long.class, result.orderId()))
                .isEqualTo(500L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_order_status_log WHERE order_id = ? AND event = 'CREATE'",
                Integer.class, result.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("支付回调 + 支付成功编排：确认库存、核销券、生成配送单")
    void payCallbackConfirmsStockAndCreatesDelivery() {
        CreateOrderResult created = createOrder(true);
        PaymentResult payment = paymentService.pay(created.orderId(), 1, "it-pay-" + UUID.randomUUID());

        assertThat(payment.status()).isEqualTo(PaymentStatus.PAYING.getCode());
        assertThat(payment.amount()).isEqualTo(5500L);

        callback(payment, "TRADE-IT-1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_payment_order WHERE id = ?", Integer.class, payment.paymentId()))
                .isEqualTo(PaymentStatus.SUCCESS.getCode());

        orderPaymentApplicationService.handlePaySuccess(created.orderId());

        assertThat(orderStatus(created.orderId())).isEqualTo(OrderStatus.WAIT_ACCEPT.getCode());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_status FROM t_order WHERE id = ?", Integer.class, created.orderId()))
                .isEqualTo(1);
        assertThat(stock("available_qty")).isEqualTo(98);
        assertThat(stock("locked_qty")).isZero();
        assertThat(stock("sold_qty")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, COUPON_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_delivery_order WHERE order_id = ?",
                Integer.class, created.orderId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_fee FROM t_delivery_order WHERE order_id = ?",
                Long.class, created.orderId())).isEqualTo(300L);

        callback(payment, "TRADE-IT-1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_order WHERE order_id = ?",
                Integer.class, created.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("待支付取消：释放库存与优惠券，取消后拒绝接单")
    void cancelBeforePayReleasesReservedAssets() {
        CreateOrderResult created = createOrder(true);

        CancelResult cancelled = refundService.cancel(
                created.orderId(), "临时有事", 1, "it-cancel-" + UUID.randomUUID());

        assertThat(cancelled.orderStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(cancelled.refundAmount()).isZero();
        assertThat(stock("available_qty")).isEqualTo(100);
        assertThat(stock("locked_qty")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, COUPON_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM t_user_coupon WHERE id = ?", Long.class, COUPON_ID)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock_log WHERE biz_id = ? AND op = 'RELEASE'",
                Integer.class, created.orderId())).isEqualTo(1);

        assertBizCode(() -> orderApplicationService.transit(
                        created.orderId(), OrderEvent.MERCHANT_ACCEPT, null,
                        OperatorType.MERCHANT, MERCHANT_ID, "INVALID-AFTER-CANCEL"),
                ErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    @DisplayName("已支付退款：网关退款成功、库存回补、支付单退款、订单取消")
    void refundAfterPaidRestoresStock() {
        CreateOrderResult created = createOrder(true);
        PaymentResult payment = paymentService.pay(created.orderId(), 1, "it-refund-pay-" + UUID.randomUUID());
        callback(payment, "TRADE-IT-2");
        orderPaymentApplicationService.handlePaySuccess(created.orderId());

        RefundResult refund = refundService.refund(
                created.orderId(), "餐品质量问题", "it-refund-" + UUID.randomUUID());

        assertThat(refund.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(refund.orderStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(refund.amount()).isEqualTo(5500L);
        assertThat(stock("available_qty")).isEqualTo(100);
        assertThat(stock("locked_qty")).isZero();
        assertThat(stock("sold_qty")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_payment_order WHERE id = ?", Integer.class, payment.paymentId()))
                .isEqualTo(PaymentStatus.REFUNDED.getCode());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT refund_status FROM t_order WHERE id = ?", Integer.class, created.orderId()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM t_order WHERE id = ?", Long.class, created.orderId()))
                .isEqualTo(5500L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_refund_order WHERE order_id = ?",
                Integer.class, created.orderId())).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, COUPON_ID)).isZero();
    }

    @Test
    @DisplayName("退款网关失败：退款单与明细回滚，订单与库存不变")
    void refundGatewayFailureRollsBack() {
        CreateOrderResult created = createOrder(true);
        PaymentResult payment = paymentService.pay(
                created.orderId(), 1, "it-fail-pay-" + UUID.randomUUID());
        callback(payment, "TRADE-IT-4");
        orderPaymentApplicationService.handlePaySuccess(created.orderId());

        // 支付单金额调小，触发 MockPaymentGateway 的金额校验失败
        jdbcTemplate.update("UPDATE t_payment_order SET amount = 100 WHERE id = ?", payment.paymentId());

        assertBizCode(() -> refundService.refund(
                        created.orderId(), "退款", "it-fail-" + UUID.randomUUID()),
                ErrorCode.REFUND_GATEWAY_FAILED);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_refund_order WHERE order_id = ?",
                Integer.class, created.orderId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_refund_item WHERE order_item_id IN "
                        + "(SELECT id FROM t_order_item WHERE order_id = ?)",
                Integer.class, created.orderId())).isZero();
        assertThat(orderStatus(created.orderId())).isEqualTo(OrderStatus.WAIT_ACCEPT.getCode());
        assertThat(stock("sold_qty")).isEqualTo(2);
        assertThat(stock("locked_qty")).isZero();
    }

    @Test
    @DisplayName("自取单履约：不生成配送单，状态机完整走到已完成")
    void pickupOrderCompletesWithoutDelivery() {
        CreateOrderResult created = createPickupOrder();
        PaymentResult payment = paymentService.pay(created.orderId(), 1, "it-pickup-pay-" + UUID.randomUUID());
        callback(payment, "TRADE-IT-3");
        orderPaymentApplicationService.handlePaySuccess(created.orderId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_delivery_order WHERE order_id = ?",
                Integer.class, created.orderId())).isZero();

        OrderStatus preparing = orderApplicationService.transit(
                created.orderId(), OrderEvent.MERCHANT_ACCEPT, null,
                OperatorType.MERCHANT, MERCHANT_ID, "ACCEPT");
        assertThat(preparing).isEqualTo(OrderStatus.PREPARING);

        OrderStatus waitPickup = orderApplicationService.transit(
                created.orderId(), OrderEvent.PREPARE_DONE, null,
                OperatorType.MERCHANT, MERCHANT_ID, "PREPARED");
        assertThat(waitPickup).isEqualTo(OrderStatus.WAIT_USER_PICKUP);

        OrderStatus pickedUp = orderApplicationService.transit(
                created.orderId(), OrderEvent.PICKUP_VERIFY, null,
                OperatorType.USER, USER_ID, "PICKUP_VERIFY");
        assertThat(pickedUp).isEqualTo(OrderStatus.PICKED_UP);

        OrderStatus completed = orderApplicationService.transit(
                created.orderId(), OrderEvent.PICKUP_COMPLETE, null,
                OperatorType.USER, USER_ID, "PICKUP_COMPLETE");
        assertThat(completed).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderStatus(created.orderId())).isEqualTo(OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("支付超时：订单取消并释放库存与优惠券")
    void timeoutReleasesReservedAssets() {
        CreateOrderResult created = createOrder(true);

        orderPaymentApplicationService.handleTimeout(created.orderId());

        assertThat(orderStatus(created.orderId())).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(stock("available_qty")).isEqualTo(100);
        assertThat(stock("locked_qty")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_user_coupon WHERE id = ?", Integer.class, COUPON_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_stock_log WHERE biz_id = ? AND op = 'RELEASE'",
                Integer.class, created.orderId())).isEqualTo(1);
    }

    private CreateOrderResult createOrder(boolean withCoupon) {
        CreateOrderCommand command = new CreateOrderCommand(
                USER_ID,
                MERCHANT_ID,
                SHOP_ID,
                FulfillmentType.DELIVERY.getCode(),
                withCoupon ? COUPON_ID : null,
                100L,
                300L,
                "张三",
                "13800000000",
                "幸福路 1 号",
                null,
                null,
                null,
                null,
                "少放辣",
                List.of(new CreateOrderCommand.ItemCommand(SKU_ID, 2))
        );
        return orderApplicationService.createOrder(command, "it-create-" + UUID.randomUUID());
    }

    private CreateOrderResult createPickupOrder() {
        CreateOrderCommand command = new CreateOrderCommand(
                USER_ID,
                MERCHANT_ID,
                SHOP_ID,
                FulfillmentType.PICKUP.getCode(),
                null,
                100L,
                300L,
                null,
                null,
                null,
                null,
                null,
                null,
                "13900000000",
                null,
                List.of(new CreateOrderCommand.ItemCommand(SKU_ID, 2))
        );
        return orderApplicationService.createOrder(command, "it-pickup-" + UUID.randomUUID());
    }

    private void callback(PaymentResult payment, String tradeNo) {
        String sign = PaymentSignUtils.sign(
                CALLBACK_SECRET, payment.paymentNo(), tradeNo, payment.amount());
        paymentService.callback(new PaymentCallbackCommand(
                payment.paymentNo(), tradeNo, payment.amount(), sign));
    }

    private int orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_order WHERE id = ?", Integer.class, orderId);
    }

    private int stock(String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM t_sku_stock WHERE shop_id = ? AND sku_id = ?",
                Integer.class, SHOP_ID, SKU_ID);
    }

    private void cleanBusinessTables() {
        List<String> tables = List.of(
                "t_stock_log", "t_stock_lock", "t_order_status_log", "t_order_discount",
                "t_order_item", "t_refund_item", "t_refund_order", "t_payment_order",
                "t_delivery_order", "t_rider_income", "t_settlement_item", "t_settlement_bill",
                "t_dispatch_record", "t_order_exception", "t_idempotency", "t_outbox_event",
                "t_order");
        // 同一连接内开关外键检查，避免连接池换连接导致 FOREIGN_KEY_CHECKS 残留为 0
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE " + table);
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private void seedFixtures() {
        jdbcTemplate.update("""
                INSERT INTO t_sku (id, shop_id, spu_id, sku_name, price, status)
                VALUES (1001, 1, 1, '招牌牛肉面', 2800, 0)
                ON DUPLICATE KEY UPDATE price = 2800, status = 0
                """);
        jdbcTemplate.update("""
                INSERT INTO t_sku_stock (shop_id, sku_id, available_qty, locked_qty, sold_qty, sold_out)
                VALUES (1, 1001, 100, 0, 0, 0)
                ON DUPLICATE KEY UPDATE available_qty = 100, locked_qty = 0, sold_qty = 0, sold_out = 0
                """);
        jdbcTemplate.update("""
                INSERT INTO t_coupon_template
                    (id, name, coupon_type, threshold_amount, discount_amount, bearer,
                     total_qty, issued_qty, status)
                VALUES (2001, '满30减5', 1, 3000, 500, 1, 1000, 1, 0)
                ON DUPLICATE KEY UPDATE threshold_amount = 3000, discount_amount = 500,
                    bearer = 1, status = 0
                """);
        jdbcTemplate.update("""
                INSERT INTO t_user_coupon
                    (id, user_id, template_id, status, order_id, locked_at, used_at, expire_at, version)
                VALUES (3001, 1, 2001, 0, NULL, NULL, NULL,
                        DATE_ADD(NOW(3), INTERVAL 30 DAY), 0)
                ON DUPLICATE KEY UPDATE user_id = 1, template_id = 2001, status = 0,
                    order_id = NULL, locked_at = NULL, used_at = NULL,
                    expire_at = DATE_ADD(NOW(3), INTERVAL 30 DAY), version = 0
                """);
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
