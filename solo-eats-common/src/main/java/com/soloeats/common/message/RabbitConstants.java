package com.soloeats.common.message;

public final class RabbitConstants {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_PAY_SUCCESS_QUEUE = "order.pay.success.queue";

    public static final String ROUTING_DELAY = "order.delay";
    public static final String ROUTING_TIMEOUT = "order.timeout";
    public static final String ROUTING_PAY_SUCCESS = "order.pay.success";

    private RabbitConstants() {
    }
}
