package com.soloeats.store.service.view;

import java.time.LocalDateTime;

/**
 * 配送范围校验结果。自取订单 deliveryFee=0、deliveryDistanceM=0。
 */
public record DeliveryCheckResult(
        boolean inRange,
        String reason,
        Long minOrderAmount,
        Long deliveryFee,
        LocalDateTime expectedDeliveryAt,
        Integer prepMinutes,
        Integer deliveryDistanceM
) {
}
