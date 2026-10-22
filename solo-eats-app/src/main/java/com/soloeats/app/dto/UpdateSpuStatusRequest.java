package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSpuStatusRequest(
        @NotNull(message = "status 不能为空")
        Integer status
) {
}
