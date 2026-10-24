package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DispatchConfigView(
        Long id,
        Integer configScope,
        Long shopId,
        BigDecimal distanceWeight,
        BigDecimal loadWeight,
        BigDecimal ratingWeight,
        BigDecimal timeoutWeight,
        Integer dispatchExpireSeconds,
        Integer maxRetry,
        Integer grabRadiusM,
        Integer grabMaxOrders,
        Integer grabEnabled,
        Integer autoDispatchEnabled,
        Integer status,
        LocalDateTime updatedAt
) {
}
