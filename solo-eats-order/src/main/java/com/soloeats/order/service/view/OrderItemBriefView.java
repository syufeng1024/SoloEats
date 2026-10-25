package com.soloeats.order.service.view;

/**
 * 骑手任务详情用的商品明细。金额单位分。
 */
public record OrderItemBriefView(
        Long skuId,
        String skuName,
        Integer quantity,
        Long unitPrice,
        Long payableAmount
) {
}
