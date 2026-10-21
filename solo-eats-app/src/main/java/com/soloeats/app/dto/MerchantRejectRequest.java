package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;

public record MerchantRejectRequest(
        @NotBlank(message = "reason 不能为空")
        String reason,

        Long merchantId
) {
}
