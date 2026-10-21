package com.soloeats.payment.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.payment.domain.entity.PaymentOrder;
import com.soloeats.payment.domain.enums.PaymentStatus;
import com.soloeats.payment.mapper.PaymentOrderMapper;
import com.soloeats.payment.service.PaymentCallbackCommand;
import com.soloeats.payment.service.PaymentResult;
import com.soloeats.payment.service.event.PaymentSuccessEvent;
import com.soloeats.payment.util.PaymentSignUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final long ORDER_ID = 90001L;
    private static final String SECRET = "test-callback-secret";
    private static final String KEY = "pay-key-1";

    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private DistributedLock distributedLock;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "callbackSecret", SECRET);
        lenient().when(distributedLock.executeWithLock(anyString(), anyLong(), anyLong(),
                        any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    @DisplayName("发起支付创建 PAYING 支付单，金额取自订单实付")
    void payCreatesPaymentOrder() {
        when(paymentOrderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.WAIT_PAY.getCode(), 5000L));
        when(paymentOrderMapper.findSuccessByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentOrderMapper.findActiveByOrderId(ORDER_ID)).thenReturn(null);
        when(paymentOrderMapper.insert(any(PaymentOrder.class))).thenAnswer(invocation -> {
            ((PaymentOrder) invocation.getArgument(0)).setId(321L);
            return 1;
        });

        PaymentResult result = paymentService.pay(ORDER_ID, 1, KEY);

        assertThat(result.paymentId()).isEqualTo(321L);
        assertThat(result.paymentNo()).startsWith("PAY");
        assertThat(result.amount()).isEqualTo(5000L);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAYING.getCode());

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("相同幂等键直接返回既有支付单，不重复加锁")
    void payIsIdempotent() {
        PaymentOrder existing = payment(1L, PaymentStatus.SUCCESS.getCode());
        when(paymentOrderMapper.selectOne(any())).thenReturn(existing);

        PaymentResult result = paymentService.pay(ORDER_ID, 1, KEY);

        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS.getCode());
        verifyNoInteractions(distributedLock, orderMapper);
    }

    @Test
    @DisplayName("非待支付订单拒绝发起支付")
    void payRejectsNonWaitPayOrder() {
        when(paymentOrderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PREPARING.getCode(), 5000L));

        assertBizCode(() -> paymentService.pay(ORDER_ID, 1, KEY), ErrorCode.ORDER_STATUS_INVALID);
        verify(paymentOrderMapper, never()).insert(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("回调签名错误直接拒绝，不落状态")
    void callbackRejectsInvalidSign() {
        PaymentCallbackCommand command = new PaymentCallbackCommand(
                "PAY-1", "TRADE-1", 5000L, "deadbeef");

        assertBizCode(() -> paymentService.callback(command), ErrorCode.PAY_SIGN_INVALID);
        verifyNoInteractions(paymentOrderMapper, eventPublisher);
    }

    @Test
    @DisplayName("回调成功将支付单置 SUCCESS 并发布支付成功事件")
    void callbackMarksSuccessAndPublishesEvent() {
        PaymentOrder paying = payment(1L, PaymentStatus.PAYING.getCode());
        when(paymentOrderMapper.selectOne(any())).thenReturn(paying);
        when(paymentOrderMapper.markSuccess(1L, "TRADE-1")).thenReturn(1);
        String sign = PaymentSignUtils.sign(SECRET, paying.getPaymentNo(), "TRADE-1", 5000L);

        paymentService.callback(new PaymentCallbackCommand(
                paying.getPaymentNo(), "TRADE-1", 5000L, sign));

        verify(paymentOrderMapper).markSuccess(1L, "TRADE-1");
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentSuccessEvent.class);
        PaymentSuccessEvent event = (PaymentSuccessEvent) eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.tradeNo()).isEqualTo("TRADE-1");
    }

    @Test
    @DisplayName("重复回调已成功支付单保持幂等")
    void callbackOnSuccessIsNoop() {
        PaymentOrder success = payment(1L, PaymentStatus.SUCCESS.getCode());
        when(paymentOrderMapper.selectOne(any())).thenReturn(success);
        String sign = PaymentSignUtils.sign(SECRET, success.getPaymentNo(), "TRADE-1", 5000L);

        paymentService.callback(new PaymentCallbackCommand(
                success.getPaymentNo(), "TRADE-1", 5000L, sign));

        verify(paymentOrderMapper, never()).markSuccess(anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("回调金额与支付单不一致时拒绝")
    void callbackAmountMismatch() {
        PaymentOrder paying = payment(1L, PaymentStatus.PAYING.getCode());
        when(paymentOrderMapper.selectOne(any())).thenReturn(paying);
        String sign = PaymentSignUtils.sign(SECRET, paying.getPaymentNo(), "TRADE-1", 4999L);

        assertBizCode(() -> paymentService.callback(new PaymentCallbackCommand(
                paying.getPaymentNo(), "TRADE-1", 4999L, sign)), ErrorCode.PAY_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("回调参数缺失时按参数错误处理")
    void callbackRejectsMissingFields() {
        assertBizCode(() -> paymentService.callback(null), ErrorCode.PARAM_INVALID);
        assertBizCode(() -> paymentService.callback(
                new PaymentCallbackCommand("", "T", 1L, "x")), ErrorCode.PARAM_INVALID);
    }

    private Order order(int status, long payAmount) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(status);
        order.setPayAmount(payAmount);
        order.setFulfillmentType(1);
        return order;
    }

    private PaymentOrder payment(long id, int status) {
        PaymentOrder payment = new PaymentOrder();
        payment.setId(id);
        payment.setPaymentNo("PAY-1");
        payment.setOrderId(ORDER_ID);
        payment.setChannel(1);
        payment.setAmount(5000L);
        payment.setStatus(status);
        payment.setIdempotencyKey(KEY);
        return payment;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
