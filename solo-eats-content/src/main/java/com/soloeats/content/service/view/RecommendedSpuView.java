package com.soloeats.content.service.view;

/** 首页猜你喜欢商品，price 单位分（该 SPU 最低在售 SKU 价）。 */
public record RecommendedSpuView(
        Long spuId,
        String spuName,
        String subtitle,
        String mainImage,
        Long shopId,
        String shopName,
        Long price,
        Integer monthSales
) {
}
