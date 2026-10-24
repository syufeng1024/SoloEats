package com.soloeats.delivery.service.view;

public record DeliveryActionView(
        Long deliveryId,
        Long orderId,
        Long riderId,
        Integer status
) {
}
