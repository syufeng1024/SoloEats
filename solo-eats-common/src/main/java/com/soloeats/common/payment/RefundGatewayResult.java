package com.soloeats.common.payment;

public record RefundGatewayResult(
        boolean success,
        String channelRefundNo,
        String message
) {
}
