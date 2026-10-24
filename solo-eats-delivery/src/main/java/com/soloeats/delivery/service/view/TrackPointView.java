package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 轨迹点响应。不含骑手手机号等隐私字段。
 */
public record TrackPointView(
        Long id,
        Long deliveryId,
        Long riderId,
        BigDecimal lng,
        BigDecimal lat,
        BigDecimal speed,
        BigDecimal accuracy,
        LocalDateTime reportAt
) {
}
