package com.soloeats.delivery.service.view;

public record PendingDispatchView(
        Long deliveryId,
        Long orderId,
        String deliveryNo,
        Long shopId,
        String pickupAddress,
        String receiverAddress,
        Long deliveryFee,
        long remainingSeconds
) {
}
