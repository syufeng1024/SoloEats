package com.soloeats.common.payment;

/**
 * 支付网关抽象。订单模块只依赖本接口，不依赖支付模块实现。
 */
public interface PaymentGateway {

    PaymentInfo findSuccessfulPayment(Long orderId);

    RefundGatewayResult refund(Long orderId, String refundNo, Long amount);
}
