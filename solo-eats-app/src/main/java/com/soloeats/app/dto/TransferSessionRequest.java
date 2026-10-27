package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransferSessionRequest(
        @NotNull(message = "toAgentId 不能为空")
        @Min(value = 1, message = "toAgentId 必须为正整数")
        Long toAgentId
) {
}
