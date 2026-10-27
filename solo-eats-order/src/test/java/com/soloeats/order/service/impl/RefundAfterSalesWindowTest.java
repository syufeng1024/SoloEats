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
 * 售后期退款（规格书 8.8）：已完成订单在售后期内可申请售后，超期或数据缺失时拒绝。
 * 既有 {@link RefundServiceImplTest} 覆盖待接单 / 备餐中 / 拒单语义，本类只补售后场景。
 */
@ExtendWith(MockitoExtension.class)
class RefundAfterSalesWindowTest {

    private static final long ORDER_ID = 91001L;
    private static final long SHOP_ID = 1L;
    private static final long MERCHANT_ID = 10L;
    private static final long USER_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final String KEY = "after-sales-key-1";

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
    @DisplayName("已完成订单在售后期内可退款：不流转状态，只回写退款金额")
    void completedOrderWithinAfterSalesWindowRefunded() {
        Order order = completedOrder(OrderStatus.COMPLETED.getCode(),
                LocalDateTime.now().minusDays(3), null, FulfillmentType.DELIVERY.getCode());
        stubRefundContext(order);
        when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(5000L)))
                .thenReturn(new RefundGatewayResult(true, "MOCKREF-AS-1", "ok"));
        when(refundOrderMapper.markSuccess(777L, "MOCKREF-AS-1")).thenReturn(1);
        when(orderMapper.markAfterSalesRefunded(ORDER_ID, 5000L)).thenReturn(1);

        RefundResult result = refundService.refund(ORDER_ID, "菜品有异物", KEY);

        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(result.amount()).isEqualTo(5000L);
        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper).markAfterSalesRefunded(ORDER_ID, 5000L);
        verify(orderMapper, never()).markRefunded(anyLong(), anyLong());
        verify(stockService).refund(ORDER_ID, SHOP_ID, SKU_ID, 2);
    }

    @Test
    @DisplayName("超过售后期拒绝退款，不触达支付网关")
    void completedOrderBeyondAfterSalesWindowRejected() {
        Order order = completedOrder(OrderStatus.COMPLETED.getCode(),
                LocalDateTime.now().minusDays(8), null, FulfillmentType.DELIVERY.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "想退货", KEY))
                .isInstanceOf(BizException.class)
                .hasMessage("已超过售后期，无法申请退款")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(paymentGateway, never()).findSuccessfulPayment(anyLong());
        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("自取单售后期起点取「已取餐」时间")
    void pickupOrderUsesPickedUpAtAsWindowStart() {
        Order order = completedOrder(OrderStatus.COMPLETED.getCode(),
                null, LocalDateTime.now().minusDays(2), FulfillmentType.PICKUP.getCode());
        stubRefundContext(order);
        when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(5000L)))
                .thenReturn(new RefundGatewayResult(true, "MOCKREF-AS-2", "ok"));
        when(refundOrderMapper.markSuccess(777L, "MOCKREF-AS-2")).thenReturn(1);
        when(orderMapper.markAfterSalesRefunded(ORDER_ID, 5000L)).thenReturn(1);

        RefundResult result = refundService.refund(ORDER_ID, "取餐后发现少餐", KEY);

        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("已完成订单缺完成时间时拒绝退款，不放大退款范围")
    void completedOrderWithoutFinishTimeRejected() {
        Order order = completedOrder(OrderStatus.COMPLETED.getCode(),
                null, null, FulfillmentType.DELIVERY.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "退款", KEY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("商家拒单语义不放宽：已完成订单仍只允许待接单")
    void merchantRejectStillLimitedToWaitAccept() {
        Order order = completedOrder(OrderStatus.COMPLETED.getCode(),
                LocalDateTime.now().minusDays(1), null, FulfillmentType.DELIVERY.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> refundService.merchantReject(ORDER_ID, MERCHANT_ID, "拒单", KEY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    private void stubRefundContext(Order order) {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(paymentGateway.findSuccessfulPayment(ORDER_ID))
                .thenReturn(new PaymentInfo(1L, "PAY-AS-1", 5000L, 20));
        lenient().when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        lenient().when(orderDiscountMapper.selectOne(any())).thenReturn(null);
        lenient().when(refundOrderMapper.insert(any(RefundOrder.class))).thenAnswer(invocation -> {
            ((RefundOrder) invocation.getArgument(0)).setId(777L);
            return 1;
        });
    }

    private Order completedOrder(int status, LocalDateTime completedAt, LocalDateTime pickedUpAt,
                                 int fulfillmentType) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(SHOP_ID);
        order.setFulfillmentType(fulfillmentType);
        order.setStatus(status);
        order.setPayAmount(5000L);
        order.setCompletedAt(completedAt);
        order.setPickedUpAt(pickedUpAt);
        return order;
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setId(11L);
        item.setOrderId(ORDER_ID);
        item.setSkuId(SKU_ID);
        item.setQuantity(2);
        item.setPayableAmount(5000L);
        return item;
    }
}
