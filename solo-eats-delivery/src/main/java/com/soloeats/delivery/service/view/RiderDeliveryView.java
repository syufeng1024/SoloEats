package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手任务列表/详情。金额单位分。
 * t_delivery_order 无收货人姓名与手机号列，receiverName 与 receiverPhoneMask 固定为 null。
 * receiverLng/receiverLat 来自 t_delivery_order 同名列，骑手详情页据此唤起地图导航。
 */
public record RiderDeliveryView(
        Long deliveryId,
        String deliveryNo,
        Long orderId,
        Long shopId,
        Integer status,
        String statusName,
        String pickupAddress,
        String receiverName,
        String receiverAddress,
        BigDecimal receiverLng,
        BigDecimal receiverLat,
        String receiverPhoneMask,
        Integer distanceM,
        Long deliveryFee,
        LocalDateTime promiseDeliverAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime dispatchExpireAt
) {
}
