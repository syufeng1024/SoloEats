package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PayRequest(
        @NotNull(message = "orderId 不能为空")
        Long orderId,

        @NotNull(message = "channel 不能为空")
        @Min(value = 1, message = "channel 只能是 1~3")
        @Max(value = 3, message = "channel 只能是 1~3")
        Integer channel
) {
}
