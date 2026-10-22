package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 配送范围校验。goodsAmount 是商品小计（分），不含配送费与打包费。
 */
public record DeliveryCheckRequest(
        @NotNull BigDecimal lng,
        @NotNull BigDecimal lat,
        @NotNull @Min(0) Long goodsAmount,
        @NotNull @Min(1) @Max(2) Integer fulfillmentType
) {
}
