package com.soloeats.product.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品库列表视图。金额单位分。
 */
public record SpuView(
        Long id,
        Long merchantId,
        Long categoryId,
        String spuName,
        String subtitle,
        String description,
        String detailHtml,
        String mainImage,
        List<String> images,
        List<String> tags,
        Long packagingFee,
        Integer minPurchaseQty,
        Integer limitPerOrder,
        Integer status,
        Integer sortWeight,
        Integer monthSales,
        LocalDateTime updatedAt
) {
}
