package com.soloeats.store.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店列表项。金额单位分。
 */
public record ShopView(
        Long id,
        Long merchantId,
        String shopNo,
        String shopName,
        String city,
        String district,
        String address,
        BigDecimal lng,
        BigDecimal lat,
        Integer status,
        String statusText,
        Integer deliveryTypes,
        Long minOrderAmount,
        Long deliveryFee,
        Long packagingFee,
        Integer prepMinutes,
        BigDecimal rating,
        Integer monthSales,
        Integer pickupEnabled,
        LocalDateTime createdAt
) {
}
