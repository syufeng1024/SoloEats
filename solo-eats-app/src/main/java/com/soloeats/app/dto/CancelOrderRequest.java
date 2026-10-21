package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelOrderRequest(
        @NotBlank(message = "reason 不能为空")
        String reason,

        @NotNull(message = "operatorType 不能为空")
        @Min(value = 1, message = "operatorType 只能是 1 或 2")
        @Max(value = 2, message = "operatorType 只能是 1 或 2")
        Integer operatorType
) {
}
