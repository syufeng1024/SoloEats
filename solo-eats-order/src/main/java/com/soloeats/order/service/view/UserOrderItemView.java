package com.soloeats.order.service.view;

public record UserOrderItemView(
        /** t_order_item.id，评价接口 CreateReviewRequest.orderItemId 必填。 */
        Long orderItemId,
        Long skuId,
        String skuName,
        Integer quantity,
        Long unitPrice,
        Long originalAmount,
        Long payableAmount
) {
}
