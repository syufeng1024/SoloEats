package com.soloeats.order.service.view;

public record MerchantOrderItemView(
        Long skuId,
        String skuName,
        Integer quantity,
        Long originalUnitPrice,
        Long payableAmount
) {
}
