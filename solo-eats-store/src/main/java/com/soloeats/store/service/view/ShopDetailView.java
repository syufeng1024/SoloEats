package com.soloeats.store.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店详情。含营业时段与自取配置，金额单位分。
 */
public record ShopDetailView(
        Long id,
        Long merchantId,
        String shopNo,
        String shopName,
        Long brandId,
        Long categoryId,
        String province,
        String city,
        String district,
        String address,
        BigDecimal lng,
        BigDecimal lat,
        String contactName,
        String contactPhone,
        String logoUrl,
        String coverUrl,
        List<String> imageUrls,
        String announcement,
        Integer status,
        String statusText,
        Integer deliveryTypes,
        Long minOrderAmount,
        Long deliveryFee,
        Long packagingFee,
        Integer prepMinutes,
        Integer deliveryRadiusM,
        BigDecimal rating,
        BigDecimal ratingTaste,
        BigDecimal ratingPackage,
        BigDecimal ratingDelivery,
        Integer monthSales,
        Integer pickupEnabled,
        Integer pickupPrepareMinutes,
        Integer pickupTimeoutMinutes,
        String pickupCodePrefix,
        Integer pickupCodeLength,
        Integer pickupCodeValidMinutes,
        Long pickupDiscount,
        Long settleAccountId,
        List<BusinessHourView> businessHours,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record BusinessHourView(
            Integer dayOfWeek,
            String startTime,
            String endTime,
            boolean crossDay
    ) {
    }
}
