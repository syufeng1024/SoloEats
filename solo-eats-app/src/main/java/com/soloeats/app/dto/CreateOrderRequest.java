package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "userId 不能为空")
        Long userId,

        @NotNull(message = "merchantId 不能为空")
        Long merchantId,

        @NotNull(message = "shopId 不能为空")
        Long shopId,

        @NotNull(message = "fulfillmentType 不能为空")
        @Min(value = 1, message = "fulfillmentType 只能是 1 或 2")
        @Max(value = 2, message = "fulfillmentType 只能是 1 或 2")
        Integer fulfillmentType,

        Long couponId,

        @Min(value = 0, message = "packagingFee 不能为负数")
        Long packagingFee,

        @Min(value = 0, message = "deliveryFee 不能为负数")
        Long deliveryFee,

        String receiverName,

        String receiverPhone,

        String receiverAddress,

        /** 收货地址簿 ID；非空时以地址簿内容覆盖收货人、手机号、地址与坐标。 */
        Long addressId,

        @DecimalMin(value = "-180.0", message = "经度取值范围 -180 ~ 180")
        @DecimalMax(value = "180.0", message = "经度取值范围 -180 ~ 180")
        BigDecimal receiverLng,

        @DecimalMin(value = "-90.0", message = "纬度取值范围 -90 ~ 90")
        @DecimalMax(value = "90.0", message = "纬度取值范围 -90 ~ 90")
        BigDecimal receiverLat,

        String pickupPhone,

        String remark,

        @NotEmpty(message = "订单明细不能为空")
        @Valid
        List<ItemRequest> items
) {

    public record ItemRequest(
            @NotNull(message = "skuId 不能为空")
            Long skuId,

            @NotNull(message = "quantity 不能为空")
            @Min(value = 1, message = "quantity 必须大于 0")
            Integer quantity
    ) {
    }
}
