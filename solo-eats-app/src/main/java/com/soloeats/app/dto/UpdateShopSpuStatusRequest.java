package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateShopSpuStatusRequest(
        @NotNull(message = "status 不能为空")
        Integer status
) {
}
