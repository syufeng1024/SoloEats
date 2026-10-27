package com.soloeats.order.service.view;

/**
 * 平台订单详情中的商品明细快照，金额单位为分。
 */
public record AdminOrderDetailItemView(
        Long itemId,
        Long skuId,
        String skuName,
        String specJson,
        String imageUrl,
        Long originalUnitPrice,
        Integer quantity,
        Long originalAmount,
        Long payableAmount,
        Integer refundedQty,
        Long refundedAmount
) {
}
