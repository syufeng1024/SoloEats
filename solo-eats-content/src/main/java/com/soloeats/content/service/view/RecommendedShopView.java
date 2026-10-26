package com.soloeats.content.service.view;

import java.math.BigDecimal;

/** 首页推荐店铺，金额单位分。 */
public record RecommendedShopView(
        Long id,
        String shopName,
        String logoUrl,
        String coverUrl,
        BigDecimal rating,
        Integer monthSales,
        Long minOrderAmount,
        Long deliveryFee,
        Integer prepMinutes
) {
}
