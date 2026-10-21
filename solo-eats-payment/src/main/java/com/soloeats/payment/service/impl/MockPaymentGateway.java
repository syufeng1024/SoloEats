package com.soloeats.payment.service.impl;

import com.soloeats.common.payment.PaymentGateway;
import com.soloeats.common.payment.PaymentInfo;
import com.soloeats.common.payment.RefundGatewayResult;
import com.soloeats.payment.domain.entity.PaymentOrder;
import com.soloeats.payment.domain.enums.PaymentStatus;
import com.soloeats.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 模拟支付网关。生产环境替换为支付宝/微信退款 API 实现，
 * 订单模块只依赖 PaymentGateway 接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public PaymentInfo findSuccessfulPayment(Long orderId) {
        PaymentOrder payment = paymentOrderMapper.findSuccessByOrderId(orderId);
        if (payment == null) {
            return null;
        }
        return new PaymentInfo(
                payment.getId(),
                payment.getPaymentNo(),
                payment.getAmount(),
                payment.getStatus()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundGatewayResult refund(Long orderId, String refundNo, Long amount) {
        PaymentOrder payment = paymentOrderMapper.findLatestByOrderId(orderId);
        if (payment == null) {
            return new RefundGatewayResult(false, null, "支付单不存在");
        }
        if (Objects.equals(payment.getStatus(), PaymentStatus.REFUNDED.getCode())) {
            return new RefundGatewayResult(true, "MOCKREF-" + refundNo, "已退款");
        }
        if (amount == null || amount <= 0 || amount > payment.getAmount()) {
            return new RefundGatewayResult(false, null, "退款金额不合法");
        }

        int rows = paymentOrderMapper.markRefunded(orderId);
        if (rows != 1) {
            PaymentOrder latest = paymentOrderMapper.findLatestByOrderId(orderId);
            if (latest != null && Objects.equals(latest.getStatus(), PaymentStatus.REFUNDED.getCode())) {
                return new RefundGatewayResult(true, "MOCKREF-" + refundNo, "已退款");
            }
            return new RefundGatewayResult(false, null, "支付单状态不允许退款");
        }
        log.info("mock refund success order={} payment={} refund={} amount={}",
                orderId, payment.getPaymentNo(), refundNo, amount);
        return new RefundGatewayResult(true, "MOCKREF-" + refundNo, "ok");
    }
}
