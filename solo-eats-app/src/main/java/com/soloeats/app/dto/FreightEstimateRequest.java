package com.soloeats.app.dto;

import com.soloeats.delivery.service.command.FreightEstimateCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费试算请求。goodsAmount 单位分；fulfillmentType 缺省按 1 平台配送处理。
 */
public record FreightEstimateRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId,

        @DecimalMin(value = "-180", message = "lng 取值范围 -180~180")
        @DecimalMax(value = "180", message = "lng 取值范围 -180~180")
        BigDecimal lng,

        @DecimalMin(value = "-90", message = "lat 取值范围 -90~90")
        @DecimalMax(value = "90", message = "lat 取值范围 -90~90")
        BigDecimal lat,

        @NotNull(message = "goodsAmount 不能为空")
        @Min(value = 0, message = "goodsAmount 不能为负数")
        Long goodsAmount,

        @DecimalMin(value = "0", message = "weightKg 不能为负数")
        BigDecimal weightKg,

        @Min(value = 0, message = "floor 不能为负数")
        Integer floor,

        LocalDateTime deliverAt,

        @Min(value = 1, message = "fulfillmentType 只能是 1平台配送 或 2到店自取")
        @Max(value = 2, message = "fulfillmentType 只能是 1平台配送 或 2到店自取")
        Integer fulfillmentType
) {

    public FreightEstimateCommand toCommand() {
        return new FreightEstimateCommand(
                shopId, lng, lat, goodsAmount, weightKg, floor, deliverAt, fulfillmentType);
    }
}
