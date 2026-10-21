package com.soloeats.app.vo;

import com.soloeats.order.service.result.CreateOrderResult;

import java.util.List;

public record OrderVO(
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
        List<ItemVO> items
) {

    public static OrderVO from(CreateOrderResult result) {
        List<ItemVO> items = result.items().stream()
                .map(ItemVO::from)
                .toList();
        return new OrderVO(
                result.orderId(),
                result.orderNo(),
                result.status(),
                result.fulfillmentType(),
                result.goodsAmount(),
                result.packagingFee(),
                result.deliveryFee(),
                result.discountAmount(),
                result.platformDiscount(),
                result.merchantDiscount(),
                result.payAmount(),
                result.pickupCode(),
                items
        );
    }

    public record ItemVO(
            Long skuId,
            String skuName,
            Integer quantity,
            Long unitPrice,
            Long originalAmount,
            Long discountAmount,
            Long payableAmount
    ) {

        static ItemVO from(CreateOrderResult.ItemResult item) {
            return new ItemVO(
                    item.skuId(),
                    item.skuName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.originalAmount(),
                    item.discountAmount(),
                    item.payableAmount()
            );
        }
    }
}
