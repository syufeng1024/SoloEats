package com.soloeats.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveDispatchConfigRequest(
        @NotNull(message = "configScope 不能为空")
        @Min(value = 1, message = "configScope 只能是 1全局 / 3门店")
        @Max(value = 3, message = "configScope 只能是 1全局 / 3门店")
        Integer configScope,

        Long shopId,

        @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
        BigDecimal distanceWeight,

        @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
        BigDecimal loadWeight,

        @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
        BigDecimal ratingWeight,

        @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
        BigDecimal timeoutWeight,

        @NotNull @Min(value = 1) @Max(value = 600)
        Integer dispatchExpireSeconds,

        @NotNull @Min(value = 0) @Max(value = 10)
        Integer maxRetry,

        @NotNull @Min(value = 100) @Max(value = 50000)
        Integer grabRadiusM,

        @NotNull @Min(value = 1) @Max(value = 50)
        Integer grabMaxOrders,

        @Min(value = 0) @Max(value = 1)
        Integer grabEnabled,

        @Min(value = 0) @Max(value = 1)
        Integer autoDispatchEnabled,

        @Min(value = 0) @Max(value = 1)
        Integer status
) {
}
