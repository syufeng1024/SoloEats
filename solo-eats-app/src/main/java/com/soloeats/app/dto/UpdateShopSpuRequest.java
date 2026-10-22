package com.soloeats.app.dto;

public record UpdateShopSpuRequest(
        Long priceOverride,
        Integer sortWeight
) {
}
