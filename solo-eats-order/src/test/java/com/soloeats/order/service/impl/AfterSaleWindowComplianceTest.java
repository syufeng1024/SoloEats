package com.soloeats.order.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.common.payment.PaymentGateway;
import com.soloeats.common.payment.PaymentInfo;
import com.soloeats.common.payment.RefundGatewayResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.RefundItemMapper;
import com.soloeats.order.mapper.RefundOrderMapper;
import com.soloeats.order.service.OrderApplicationService;
import com.soloeats.order.service.result.RefundResult;
import com.soloeats.product.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后期与结算冻结符合性用例。需求来源：规格书 8.2、8.8、11.2「售后」。
 *
 * <p>验收断言（与当前实现一致，全部为防回归）：
 * <ol>
 *   <li>订单完成后 <b>售后期内</b>（默认 7 天）可申请退款 → 退款成功；</li>
 *   <li>订单完成后 <b>超出售后期</b> 拒绝 → 抛 {@code ORDER_STATUS_INVALID}，且不产生退款单、不调退款网关；</li>
 *   <li>自取单与配送单同口径，售后期起点取「已取餐 / 已送达」时刻
 *       （{@code picked_up_at} 优先，否则 {@code completed_at}）；</li>
 *   <li>未完成订单（待接单/备餐中）的退款不受售后期约束（回归保护）。</li>
 * </ol>
 *
 * <p><b>TODO(D-04 待决策)</b>：已完成订单的售后期退款<b>不流转订单状态</b>，
 * 由 {@code OrderMapper#markAfterSalesRefunded}（{@code WHERE id=? AND status=60}）
 * 只回写 {@code refund_status=2} / {@code refunded_amount}；与规格书 8.9 的独立「已退款」状态
 * 存在口径差异，待产品在方案 A/B 间裁定。
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleWindowComplianceTest {

    private static final long ORDER_ID = 90001L;
    private static final long SHOP_ID = 1L;
    private static final long MERCHANT_ID = 10L;
    private static final long USER_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long PAY_AMOUNT = 5000L;
    private static final String KEY = "after-sale-key";

    /** 远期下界：任何合理配置（7~365 天）都会落在售后期之外。 */
    private static final long BEYOND_WINDOW_DAYS = 400L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderDiscountMapper orderDiscountMapper;
    @Mock
    private RefundOrderMapper refundOrderMapper;
    @Mock
    private RefundItemMapper refundItemMapper;
    @Mock
    private OrderApplicationService orderApplicationService;
    @Mock
    private StockService stockService;
    @Mock
    private CouponService couponService;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private DistributedLock distributedLock;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private RefundServiceImpl refundService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(),
                        any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    @DisplayName("配送单已完成且在售后期内（完成 1 天前）：退款成功，订单状态保持 COMPLETED 只回写 refund_status")
    void refundWithinAfterSalesWindowSucceeds() {
        stubRefundableOrder(OrderStatus.COMPLETED, FulfillmentType.DELIVERY, 1L);
        stubGatewaySuccess();

        RefundResult result = refundService.refund(ORDER_ID, "商品质量问题", KEY);

        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(result.amount()).isEqualTo(PAY_AMOUNT);
        // TODO(D-04 待决策)：售后不改订单状态，只回写 refund_status=2（markAfterSalesRefunded）
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        verify(paymentGateway).refund(eq(ORDER_ID), anyString(), eq(PAY_AMOUNT));
        verify(stockService).refund(ORDER_ID, SHOP_ID, SKU_ID, 1);
        verify(refundOrderMapper).insert(any(RefundOrder.class));
        verify(orderMapper).markAfterSalesRefunded(eq(ORDER_ID), any());
        verify(orderMapper, never()).markRefunded(any(), any());
        verify(orderApplicationService, never())
                .transit(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("配送单已完成但超出售后期（完成 400 天前）：拒绝退款且零副作用")
    void refundBeyondAfterSalesWindowRejected() {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(OrderStatus.COMPLETED, FulfillmentType.DELIVERY, BEYOND_WINDOW_DAYS));

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "商品质量问题", KEY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
        verify(refundOrderMapper, never()).insert(any(RefundOrder.class));
    }

    @Test
    @DisplayName("自取单已完成且在售后期内：以「已取餐」时刻为起点可退，订单状态仍为 COMPLETED")
    void refundPickupOrderWithinWindowSucceeds() {
        stubRefundableOrder(OrderStatus.COMPLETED, FulfillmentType.PICKUP, 1L);
        stubGatewaySuccess();

        RefundResult result = refundService.refund(ORDER_ID, "没去取餐", KEY);

        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        // TODO(D-04 待决策)：自取单售后同样不改订单状态
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        verify(paymentGateway).refund(eq(ORDER_ID), anyString(), eq(PAY_AMOUNT));
        verify(orderMapper).markAfterSalesRefunded(eq(ORDER_ID), any());
        verify(orderMapper, never()).markRefunded(any(), any());
    }

    @Test
    @DisplayName("自取单已完成但超出售后期：拒绝退款")
    void refundPickupOrderBeyondWindowRejected() {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(OrderStatus.COMPLETED, FulfillmentType.PICKUP, BEYOND_WINDOW_DAYS));

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "没去取餐", KEY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("待接单订单不受售后期约束：通过可退性校验（无支付记录时报 PAYMENT_NOT_FOUND）")
    void refundWaitAcceptOrderUnaffectedByWindow() {
        Order order = order(OrderStatus.WAIT_ACCEPT, FulfillmentType.DELIVERY, null);
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(paymentGateway.findSuccessfulPayment(ORDER_ID)).thenReturn(null);

        // 抛出的是"没有支付记录"，说明已经越过状态/售后期校验
        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "不想要了", KEY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("同一幂等键重复申请：返回既有退款单，不重复调网关")
    void repeatedRefundKeyIsIdempotent() {
        RefundOrder existing = new RefundOrder();
        existing.setId(555L);
        existing.setOrderId(ORDER_ID);
        existing.setRefundNo("RF-555");
        existing.setAmount(PAY_AMOUNT);
        existing.setStatus(RefundStatus.SUCCESS.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(existing);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(
                order(OrderStatus.COMPLETED, FulfillmentType.DELIVERY, 1L));

        RefundResult result = refundService.refund(ORDER_ID, "商品质量问题", KEY);

        assertThat(result.refundId()).isEqualTo(555L);
        assertThat(result.amount()).isEqualTo(PAY_AMOUNT);
        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    private void stubRefundableOrder(OrderStatus status, FulfillmentType type, Long completedDaysAgo) {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(status, type, completedDaysAgo));
        lenient().when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        lenient().when(orderDiscountMapper.selectOne(any())).thenReturn(null);
    }

    private void stubGatewaySuccess() {
        // 全部用 lenient：非本用例路径回滚时避免噪音型 UnnecessaryStubbingException
        lenient().when(paymentGateway.findSuccessfulPayment(ORDER_ID))
                .thenReturn(new PaymentInfo(1L, "PAY-1", PAY_AMOUNT, 20));
        lenient().when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(PAY_AMOUNT)))
                .thenReturn(new RefundGatewayResult(true, "MOCKREF-1", "ok"));
        // insert 为 mock，退款单 id 为 null，故用 any() 而非 anyLong() 匹配
        lenient().when(refundOrderMapper.markSuccess(any(), anyString())).thenReturn(1);
        lenient().when(orderApplicationService.transit(eq(ORDER_ID), any(), any(), any(), any(),
                        anyString()))
                .thenReturn(OrderStatus.CANCELLED);
        // 取消路径（非终态）：订单置 70 后回写退款金额
        lenient().when(orderMapper.markRefunded(eq(ORDER_ID), any())).thenReturn(1);
        // 售后路径（已完成的终态）：不改订单状态，只回写 refund_status = 2
        lenient().when(orderMapper.markAfterSalesRefunded(eq(ORDER_ID), any())).thenReturn(1);
    }

    private Order order(OrderStatus status, FulfillmentType type, Long completedDaysAgo) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO-90001");
        order.setUserId(USER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(SHOP_ID);
        order.setStatus(status.getCode());
        order.setFulfillmentType(type.getCode());
        order.setPayAmount(PAY_AMOUNT);
        if (completedDaysAgo != null) {
            LocalDateTime completedAt = LocalDateTime.now().minusDays(completedDaysAgo);
            order.setCompletedAt(completedAt);
            order.setSettleEligibleAt(completedAt.plusDays(7));
        }
        return order;
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setId(7001L);
        item.setOrderId(ORDER_ID);
        item.setShopId(SHOP_ID);
        item.setSkuId(SKU_ID);
        item.setQuantity(1);
        item.setPayableAmount(PAY_AMOUNT);
        return item;
    }
}
