package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GoOnlineRequest(
        @NotNull(message = "online 不能为空")
        Boolean online,

        BigDecimal lng,

        BigDecimal lat,

        @Size(max = 128, message = "下线原因不能超过 128 个字符")
        String reason
) {
}
