package com.soloeats.delivery.service.command;

import java.math.BigDecimal;

public record SaveDispatchConfigCommand(
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
        Integer status
) {
}
