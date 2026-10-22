package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record PutOnShopRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId,
        @NotNull(message = "spuId 不能为空")
        Long spuId,
        Long priceOverride,
        Integer sortWeight
) {
}
