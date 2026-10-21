package com.soloeats.common.message;

/**
 * 订单支付超时 MQ 消息。下单成功后投递到延迟队列。
 */
public record OrderTimeoutMessage(
        Long orderId,
        String traceId
) {
}
