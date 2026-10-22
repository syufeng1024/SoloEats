package com.soloeats.product.service.view;

import java.time.LocalDateTime;

/**
 * 门店商品视图。auditStatus：0免审 1待审 2通过 3驳回。
 */
public record ShopSpuView(
        Long id,
        Long shopId,
        Long spuId,
        String spuName,
        String mainImage,
        Long priceOverride,
        Long skuMinPrice,
        Integer status,
        Integer auditStatus,
        Integer sortWeight,
        Integer monthSales,
        LocalDateTime suspendedUntil,
        String remark,
        LocalDateTime updatedAt
) {
}
