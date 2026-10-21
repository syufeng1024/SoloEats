package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
