package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PickupVerifyRequest(
        @NotBlank(message = "取餐码不能为空")
        @Size(max = 32, message = "取餐码最长 32 位")
        String pickupCode
) {
}
