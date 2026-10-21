package com.soloeats.common.message;

/**
 * 支付成功 MQ 消息。支付模块发布，订单模块消费。
 */
public record OrderPaySuccessMessage(
        Long orderId,
        String paymentNo,
        String tradeNo,
        Long amount,
        String traceId
) {
}
