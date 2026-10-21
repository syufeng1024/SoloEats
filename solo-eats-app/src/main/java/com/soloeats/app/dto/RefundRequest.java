package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
        @NotBlank(message = "reason 不能为空")
        String reason
) {
}
