package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRiderLimitsRequest(
        @NotNull(message = "最大同时接单量不能为空")
        @Min(value = 1, message = "最大同时接单量必须大于 0")
        Integer maxConcurrentOrders,

        @NotNull(message = "接单半径不能为空")
        @Min(value = 100, message = "接单半径不能小于 100 米")
        Integer pickupRadiusM
) {
}
