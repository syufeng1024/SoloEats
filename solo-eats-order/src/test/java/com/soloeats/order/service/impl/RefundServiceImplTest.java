package com.soloeats.order.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.common.payment.PaymentGateway;
import com.soloeats.common.payment.PaymentInfo;
import com.soloeats.common.payment.RefundGatewayResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderDiscount;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.RefundItemMapper;
import com.soloeats.order.mapper.RefundOrderMapper;
import com.soloeats.order.service.OrderApplicationService;
import com.soloeats.order.service.result.CancelResult;
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

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    private static final long ORDER_ID = 90001L;
    private static final long SHOP_ID = 1L;
    private static final long MERCHANT_ID = 10L;
    private static final long USER_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long COUPON_ID = 3001L;
    private static final String KEY = "refund-key-1";

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
    @DisplayName("相同幂等键直接返回既有退款单，不进入加锁与退款流程")
    void refundIsIdempotent() {
        RefundOrder existing = refund(555L, RefundStatus.SUCCESS.getCode(), 5000L);
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(existing);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PREPARING.getCode()));

        RefundResult result = refundService.refund(ORDER_ID, "不想要了", KEY);

        assertThat(result.refundId()).isEqualTo(555L);
        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(result.amount()).isEqualTo(5000L);
        verifyNoInteractions(distributedLock, paymentGateway);
    }

    @Test
    @DisplayName("退款成功：落退款单/明细、调网关、回补库存、回滚优惠券、订单置取消")
    void refundSuccessWritesAllSideEffects() {
        stubRefundContext(OrderStatus.WAIT_ACCEPT.getCode(), true);
        when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(5000L)))
                .thenReturn(new RefundGatewayResult(true, "MOCKREF-1", "ok"));
        when(refundOrderMapper.markSuccess(777L, "MOCKREF-1")).thenReturn(1);
        when(orderApplicationService.transit(eq(ORDER_ID), any(), any(), any(), any(), anyString()))
                .thenReturn(OrderStatus.CANCELLED);
        when(orderMapper.markRefunded(ORDER_ID, 5000L)).thenReturn(1);

        RefundResult result = refundService.refund(ORDER_ID, "商家缺货", KEY);

        assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCESS.getCode());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(result.channelRefundNo()).isEqualTo("MOCKREF-1");

        verify(refundOrderMapper).markSuccess(777L, "MOCKREF-1");
        verify(stockService).refund(ORDER_ID, SHOP_ID, SKU_ID, 2);
        verify(couponService).rollback(COUPON_ID, ORDER_ID);
        verify(orderItemMapper).markAllRefunded(ORDER_ID);
        verify(orderMapper).markRefunded(ORDER_ID, 5000L);
    }

    @Test
    @DisplayName("订单无成功支付记录时拒绝退款")
    void refundWithoutSuccessfulPaymentFails() {
        stubRefundContext(OrderStatus.WAIT_ACCEPT.getCode(), false);
        when(paymentGateway.findSuccessfulPayment(ORDER_ID)).thenReturn(null);

        assertBizCode(() -> refundService.refund(ORDER_ID, "退款", KEY),
                ErrorCode.PAYMENT_NOT_FOUND);

        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
        verify(stockService, never()).refund(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("支付网关退款失败时不回补库存、不流转订单")
    void refundGatewayFailureKeepsOrderUntouched() {
        stubRefundContext(OrderStatus.WAIT_ACCEPT.getCode(), false);
        when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(5000L)))
                .thenReturn(new RefundGatewayResult(false, null, "渠道超时"));

        assertBizCode(() -> refundService.refund(ORDER_ID, "退款", KEY),
                ErrorCode.REFUND_GATEWAY_FAILED);

        verify(stockService, never()).refund(anyLong(), anyLong(), anyLong(), anyInt());
        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markRefunded(anyLong(), anyLong());
    }

    @Test
    @DisplayName("待支付订单取消：释放库存与优惠券，不产生退款单")
    void cancelWaitPayReleasesReservedAssets() {
        Order order = order(OrderStatus.WAIT_PAY.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(orderDiscountMapper.selectOne(any())).thenReturn(discount());
        when(orderApplicationService.transit(eq(ORDER_ID), any(), any(), any(), any(), anyString()))
                .thenReturn(OrderStatus.CANCELLED);
        when(orderMapper.markCancelled(ORDER_ID, "临时有事")).thenReturn(1);

        CancelResult result = refundService.cancel(ORDER_ID, "临时有事", 1, KEY);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(result.refundNo()).isNull();
        assertThat(result.refundAmount()).isZero();
        verify(stockService).release(ORDER_ID, SHOP_ID, SKU_ID, 2);
        verify(couponService).release(COUPON_ID, ORDER_ID);
        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("备餐中订单取消走全额退款")
    void cancelPreparingDelegatesToRefund() {
        stubRefundContext(OrderStatus.PREPARING.getCode(), true);
        when(paymentGateway.refund(eq(ORDER_ID), anyString(), eq(5000L)))
                .thenReturn(new RefundGatewayResult(true, "MOCKREF-2", "ok"));
        when(refundOrderMapper.markSuccess(777L, "MOCKREF-2")).thenReturn(1);
        when(orderApplicationService.transit(eq(ORDER_ID), any(), any(), any(), any(), anyString()))
                .thenReturn(OrderStatus.CANCELLED);
        when(orderMapper.markRefunded(ORDER_ID, 5000L)).thenReturn(1);

        CancelResult result = refundService.cancel(ORDER_ID, "商家做不了", 1, KEY);

        assertThat(result.refundNo()).isNotBlank();
        assertThat(result.refundAmount()).isEqualTo(5000L);
        verify(stockService).refund(ORDER_ID, SHOP_ID, SKU_ID, 2);
    }

    @Test
    @DisplayName("配送中订单拒绝取消")
    void cancelDeliveringOrderRejected() {
        Order order = order(OrderStatus.DELIVERING.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        assertBizCode(() -> refundService.cancel(ORDER_ID, "取消", 1, KEY),
                ErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    @DisplayName("已取消订单重复取消返回最近一次退款单信息")
    void cancelAlreadyCancelledReturnsLatestRefund() {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.CANCELLED.getCode()));
        when(refundOrderMapper.findLatestByOrderId(ORDER_ID))
                .thenReturn(refund(600L, RefundStatus.SUCCESS.getCode(), 5000L));

        CancelResult result = refundService.cancel(ORDER_ID, "取消", 1, KEY);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED.getCode());
        assertThat(result.refundNo()).isEqualTo("RF-600");
        assertThat(result.refundAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("商家拒单只允许待接单状态")
    void merchantRejectOnlyAllowedInWaitAccept() {
        Order preparing = order(OrderStatus.PREPARING.getCode());
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(preparing);

        assertBizCode(() -> refundService.merchantReject(ORDER_ID, MERCHANT_ID, "拒单", KEY),
                ErrorCode.ORDER_STATUS_INVALID);
        verify(paymentGateway, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("参数校验：订单号为空或原因为空白直接拒绝")
    void argumentValidation() {
        assertBizCode(() -> refundService.refund(null, "原因", KEY), ErrorCode.PARAM_INVALID);
        assertBizCode(() -> refundService.refund(ORDER_ID, "  ", KEY), ErrorCode.PARAM_INVALID);
        assertBizCode(() -> refundService.cancel(ORDER_ID, "原因", 3, KEY), ErrorCode.PARAM_INVALID);
    }

    private void stubRefundContext(int orderStatus, boolean withCoupon) {
        when(refundOrderMapper.findByIdempotencyKey(KEY)).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(orderStatus));
        when(paymentGateway.findSuccessfulPayment(ORDER_ID))
                .thenReturn(new PaymentInfo(1L, "PAY-1", 5000L, 20));
        lenient().when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        lenient().when(orderDiscountMapper.selectOne(any())).thenReturn(withCoupon ? discount() : null);
        lenient().when(refundOrderMapper.insert(any(RefundOrder.class))).thenAnswer(invocation -> {
            ((RefundOrder) invocation.getArgument(0)).setId(777L);
            return 1;
        });
    }

    private Order order(int status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(SHOP_ID);
        order.setFulfillmentType(1);
        order.setStatus(status);
        order.setPayAmount(5000L);
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

    private OrderDiscount discount() {
        OrderDiscount discount = new OrderDiscount();
        discount.setOrderId(ORDER_ID);
        discount.setDiscountType(1);
        discount.setSourceId(COUPON_ID);
        discount.setAmount(500L);
        return discount;
    }

    private RefundOrder refund(long id, int status, long amount) {
        RefundOrder refund = new RefundOrder();
        refund.setId(id);
        refund.setRefundNo("RF-" + id);
        refund.setOrderId(ORDER_ID);
        refund.setAmount(amount);
        refund.setStatus(status);
        refund.setChannelRefundNo("MOCKREF-" + id);
        return refund;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
