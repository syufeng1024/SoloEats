package com.soloeats.product.service.view;

import java.time.LocalDateTime;

/**
 * 商家 SKU + 库存合并视图。price 单位为分。
 */
public record MerchantSkuView(
        Long skuId,
        Long shopId,
        Long spuId,
        String skuName,
        String specJson,
        String imageUrl,
        Long price,
        Integer status,
        Integer availableQty,
        Integer lockedQty,
        Integer soldQty,
        Boolean soldOut,
        Integer stockVersion,
        LocalDateTime updatedAt
) {
}
