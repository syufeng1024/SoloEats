package com.soloeats.order.service.result;

public record RefundResult(
        Long refundId,
        String refundNo,
        Long orderId,
        Long amount,
        Integer refundStatus,
        Integer orderStatus,
        String channelRefundNo
) {
}
