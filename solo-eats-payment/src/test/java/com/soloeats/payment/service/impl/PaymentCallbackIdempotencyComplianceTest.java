package com.soloeats.payment.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.payment.domain.entity.PaymentOrder;
import com.soloeats.payment.domain.enums.PaymentStatus;
import com.soloeats.payment.mapper.PaymentOrderMapper;
import com.soloeats.payment.service.PaymentCallbackCommand;
import com.soloeats.payment.service.event.PaymentSuccessEvent;
import com.soloeats.payment.util.PaymentSignUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调幂等与验签符合性用例。需求来源：规格书 9.2「支付回调幂等」、9.3、11.2「支付」。
 *
 * <p>断言要点：
 * <ol>
 *   <li>同一 {@code paymentNo} 的回调重复到达，只落一次成功、只发一次事件（不重复履约）；</li>
 *   <li>并发下条件更新落空（rows=0）但库内已是成功态时，静默返回不抛错、不发事件；</li>
 *   <li>金额与验签被篡改时拒绝，且不得改写支付单。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackIdempotencyComplianceTest {

    private static final String SECRET = "test-callback-secret";
    private static final String PAYMENT_NO = "PAY202610280001";
    private static final String TRADE_NO = "ALIPAY-TRADE-1";
    private static final long PAY_AMOUNT = 5000L;
    private static final long PAYMENT_ID = 77L;

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
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "callbackSecret", SECRET);
    }

    @Test
    @DisplayName("重复回调：成功态短路，只落一次成功、只发一次支付成功事件")
    void duplicateCallbackAppliesOnce() {
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(paymentOrder(PaymentStatus.PAYING.getCode()),
                        paymentOrder(PaymentStatus.SUCCESS.getCode()));
        when(paymentOrderMapper.markSuccess(PAYMENT_ID, TRADE_NO)).thenReturn(1);

        paymentService.callback(validCommand(PAY_AMOUNT));
        paymentService.callback(validCommand(PAY_AMOUNT));

        verify(paymentOrderMapper, times(1)).markSuccess(PAYMENT_ID, TRADE_NO);
        verify(eventPublisher, times(1)).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("回调连发三次：仍只落一次成功、只发一次事件")
    void threeCallbacksApplyOnce() {
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(paymentOrder(PaymentStatus.PAYING.getCode()),
                        paymentOrder(PaymentStatus.SUCCESS.getCode()),
                        paymentOrder(PaymentStatus.SUCCESS.getCode()));
        when(paymentOrderMapper.markSuccess(PAYMENT_ID, TRADE_NO)).thenReturn(1);

        paymentService.callback(validCommand(PAY_AMOUNT));
        paymentService.callback(validCommand(PAY_AMOUNT));
        paymentService.callback(validCommand(PAY_AMOUNT));

        verify(paymentOrderMapper, times(1)).markSuccess(PAYMENT_ID, TRADE_NO);
        verify(eventPublisher, times(1)).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("并发落空：条件更新影响 0 行但库内已成功时静默返回，不抛错不重复发事件")
    void lostRaceReturnsQuietly() {
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(paymentOrder(PaymentStatus.PAYING.getCode()));
        when(paymentOrderMapper.markSuccess(PAYMENT_ID, TRADE_NO)).thenReturn(0);
        when(paymentOrderMapper.selectById(PAYMENT_ID))
                .thenReturn(paymentOrder(PaymentStatus.SUCCESS.getCode()));

        assertThatCode(() -> paymentService.callback(validCommand(PAY_AMOUNT)))
                .doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("金额被篡改（回调 49.99 元 ≠ 支付单 50.00 元）：拒绝且不改写支付单")
    void tamperedAmountRejected() {
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(paymentOrder(PaymentStatus.PAYING.getCode()));

        // 用篡改后的金额重新签名，模拟"签名合法但金额不符"
        PaymentCallbackCommand tampered = new PaymentCallbackCommand(
                PAYMENT_NO, TRADE_NO, PAY_AMOUNT - 1,
                PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, PAY_AMOUNT - 1));

        assertThatThrownBy(() -> paymentService.callback(tampered))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PAY_AMOUNT_MISMATCH.getCode());
        verify(paymentOrderMapper, never()).markSuccess(any(), any());
    }

    @Test
    @DisplayName("验签不通过：拒绝且不改写支付单")
    void tamperedSignRejected() {
        // 验签在查支付单之前执行，故此处不 stub selectOne（否则构成冗余桩）
        PaymentCallbackCommand badSign = new PaymentCallbackCommand(
                PAYMENT_NO, TRADE_NO, PAY_AMOUNT, "deadbeef");

        assertThatThrownBy(() -> paymentService.callback(badSign))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PAY_SIGN_INVALID.getCode());
        verify(paymentOrderMapper, never()).markSuccess(any(), any());
    }

    @Test
    @DisplayName("支付单不存在：拒绝且不发事件")
    void unknownPaymentNoRejected() {
        when(paymentOrderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> paymentService.callback(validCommand(PAY_AMOUNT)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND.getCode());
        verify(eventPublisher, never()).publishEvent(any(PaymentSuccessEvent.class));
    }

    private PaymentCallbackCommand validCommand(long amount) {
        return new PaymentCallbackCommand(PAYMENT_NO, TRADE_NO, amount,
                PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, amount));
    }

    private PaymentOrder paymentOrder(int status) {
        PaymentOrder order = new PaymentOrder();
        order.setId(PAYMENT_ID);
        order.setPaymentNo(PAYMENT_NO);
        order.setOrderId(90001L);
        order.setChannel(1);
        order.setAmount(PAY_AMOUNT);
        order.setStatus(status);
        order.setIdempotencyKey("pay-key-1");
        return order;
    }
}
