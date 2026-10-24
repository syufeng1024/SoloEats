package com.soloeats.delivery.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单个轨迹点。reportAt 为空时由服务端补 now()，非空时按原值落库（允许乱序）。
 */
public record TrackPointCommand(
        Long deliveryId,
        BigDecimal lng,
        BigDecimal lat,
        BigDecimal speed,
        BigDecimal accuracy,
        LocalDateTime reportAt
) {
}
