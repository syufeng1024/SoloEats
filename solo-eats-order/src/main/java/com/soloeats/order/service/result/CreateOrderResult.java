package com.soloeats.order.service.result;

import java.util.List;

public record CreateOrderResult(
        Long orderId,
        String orderNo,
        Integer status,
        Integer fulfillmentType,
        Long goodsAmount,
        Long packagingFee,
        Long deliveryFee,
        Long discountAmount,
        Long platformDiscount,
        Long merchantDiscount,
        Long payAmount,
        String pickupCode,
        List<ItemResult> items
) {

    public record ItemResult(
            Long skuId,
            String skuName,
            Integer quantity,
            Long unitPrice,
            Long originalAmount,
            Long discountAmount,
            Long payableAmount
    ) {
    }
}
