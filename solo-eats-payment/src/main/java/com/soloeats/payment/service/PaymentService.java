package com.soloeats.payment.service;

public interface PaymentService {

    /**
     * 发起模拟支付。仅允许 WAIT_PAY 订单，返回 PAYING 支付单。
     */
    PaymentResult pay(Long orderId, Integer channel, String idempotencyKey);

    /**
     * 模拟支付平台回调。校验签名、金额和幂等后标记支付成功，
     * 事务提交后发送 order.pay.success 消息。
     */
    void callback(PaymentCallbackCommand command);
}
