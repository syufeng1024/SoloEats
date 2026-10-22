package com.soloeats.store.service.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门店新增/编辑。金额单位为分，经纬度保持 BigDecimal 精度。
 * merchantId 只在平台侧生效；商家侧一律取登录令牌。
 */
public record SaveShopCommand(
        Long merchantId,
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
        Integer deliveryTypes,
        Long minOrderAmount,
        Long deliveryFee,
        Long packagingFee,
        Integer prepMinutes,
        Integer deliveryRadiusM,
        Integer sortWeight,
        Long settleAccountId
) {

    /**
     * 自取配置。pickupCodeValidMinutes 是取餐码有效期，
     * pickupTimeoutMinutes 是超时未取时长，两者语义不同。
     */
    public record Pickup(
            Boolean pickupEnabled,
            Integer pickupPrepareMinutes,
            Integer pickupTimeoutMinutes,
            String pickupCodePrefix,
            Integer pickupCodeLength,
            Integer pickupCodeValidMinutes,
            Long pickupDiscount
    ) {
    }
}
