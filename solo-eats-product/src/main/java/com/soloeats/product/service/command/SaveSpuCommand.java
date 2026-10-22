package com.soloeats.product.service.command;

import java.util.List;

/**
 * SPU 保存入参。金额单位为分；merchantId 为空表示平台公共商品库。
 */
public record SaveSpuCommand(
        Long categoryId,
        Long merchantId,
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
        Integer sortWeight
) {
}
