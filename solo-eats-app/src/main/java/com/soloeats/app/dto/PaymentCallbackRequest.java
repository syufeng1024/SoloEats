package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
        @NotBlank(message = "paymentNo 不能为空")
        String paymentNo,

        @NotBlank(message = "tradeNo 不能为空")
        String tradeNo,

        @NotNull(message = "amount 不能为空")
        @Min(value = 1, message = "amount 必须大于 0")
        Long amount,

        @NotBlank(message = "sign 不能为空")
        String sign
) {
}
