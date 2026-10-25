package com.soloeats.order.service.view;

public record UserOrderItemView(
        Long skuId,
        String skuName,
        Integer quantity,
        Long unitPrice,
        Long originalAmount,
        Long payableAmount
) {
}
