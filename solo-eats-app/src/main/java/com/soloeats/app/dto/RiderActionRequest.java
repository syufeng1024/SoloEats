package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record RiderActionRequest(
        @NotNull(message = "riderId 不能为空")
        Long riderId
) {
}
