package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record BindFreightTemplateRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId
) {
}
