package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台配送单列表项。金额单位分。
 */
public record DeliveryOrderView(
        Long id,
        String deliveryNo,
        Long orderId,
        Long shopId,
        Long riderId,
        Integer status,
        String pickupAddress,
        BigDecimal pickupLng,
        BigDecimal pickupLat,
        String receiverAddress,
        BigDecimal receiverLng,
        BigDecimal receiverLat,
        Integer distanceM,
        Long deliveryFee,
        Integer dispatchCount,
        LocalDateTime promiseDeliverAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {
}
