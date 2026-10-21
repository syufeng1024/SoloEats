package com.soloeats.order.service.result;

public record CancelResult(
        Long orderId,
        Integer orderStatus,
        String refundNo,
        Long refundAmount
) {
}
