package com.soloeats.product.service.command;

/**
 * 商家把 SPU 上架到门店。
 */
public record PutOnShopCommand(
        Long shopId,
        Long spuId,
        Long priceOverride,
        Integer sortWeight
) {
}
