package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "role 不能为空")
        String role,

        Long userId,

        Long merchantId,

        Long shopId,

        Long riderId
) {
}
