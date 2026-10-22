package com.soloeats.product.service.command;

/**
 * 门店商品改价/排序。priceOverride 为空表示沿用 SKU 价。
 */
public record UpdateShopSpuCommand(
        Long priceOverride,
        Integer sortWeight
) {
}
