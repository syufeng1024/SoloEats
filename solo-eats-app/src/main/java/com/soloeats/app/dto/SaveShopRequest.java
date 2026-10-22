package com.soloeats.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门店新增/编辑。金额单位分，经纬度用 BigDecimal 避免精度丢失。
 * 商家端不允许通过本请求修改 merchantId / rating / month_sales。
 */
public record SaveShopRequest(
        Long merchantId,
        @NotBlank @Size(max = 128) String shopName,
        Long brandId,
        Long categoryId,
        @Size(max = 64) String province,
        @Size(max = 64) String city,
        @Size(max = 64) String district,
        @NotBlank @Size(max = 255) String address,
        @NotNull BigDecimal lng,
        @NotNull BigDecimal lat,
        @Size(max = 64) String contactName,
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确") String contactPhone,
        @Size(max = 512) String logoUrl,
        @Size(max = 512) String coverUrl,
        @Size(max = 9) List<@Size(max = 512) String> imageUrls,
        @Size(max = 500) String announcement,
        @NotNull @Min(1) @Max(3) Integer deliveryTypes,
        @NotNull @Min(0) Long minOrderAmount,
        @Min(0) Long deliveryFee,
        @Min(0) Long packagingFee,
        @Min(1) @Max(180) Integer prepMinutes,
        @Min(100) @Max(50000) Integer deliveryRadiusM,
        Integer sortWeight,
        Long settleAccountId
) {

    public record StatusRequest(
            @NotNull @Min(0) @Max(3) Integer status
    ) {
    }

    /**
     * pickupCodeValidMinutes 是取餐码有效期；pickupTimeoutMinutes 是超时未取时长。
     */
    public record PickupRequest(
            Boolean pickupEnabled,
            @Min(1) @Max(120) Integer pickupPrepareMinutes,
            @Min(10) @Max(1440) Integer pickupTimeoutMinutes,
            @Size(max = 8) String pickupCodePrefix,
            @Min(4) @Max(10) Integer pickupCodeLength,
            @Min(1) @Max(120) Integer pickupCodeValidMinutes,
            @Min(0) Long pickupDiscount
    ) {
    }

    public record DecorationRequest(
            @NotNull JsonNode config
    ) {
    }
}
