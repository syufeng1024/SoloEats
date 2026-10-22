package com.soloeats.store.service.view;

import java.math.BigDecimal;

/**
 * 附近门店列表项。distanceM 为 Haversine 距离，四舍五入到米。
 */
public record NearbyShopView(
        Long id,
        String shopName,
        String logoUrl,
        String coverUrl,
        String address,
        BigDecimal lng,
        BigDecimal lat,
        Integer status,
        Integer deliveryTypes,
        Long minOrderAmount,
        Long deliveryFee,
        Integer prepMinutes,
        BigDecimal rating,
        Integer monthSales,
        Integer pickupEnabled,
        Integer distanceM
) {
}
