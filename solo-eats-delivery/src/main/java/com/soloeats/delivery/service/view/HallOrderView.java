package com.soloeats.delivery.service.view;

import java.time.LocalDateTime;

public record HallOrderView(
        Long deliveryId,
        String deliveryNo,
        Long shopId,
        String pickupAddress,
        String receiverAddress,
        Integer distanceM,
        Long deliveryFee,
        LocalDateTime createdAt
) {
}
