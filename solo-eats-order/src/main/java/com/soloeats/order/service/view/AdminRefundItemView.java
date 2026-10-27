package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * 平台售后详情中的退款明细，金额单位为分。
 */
public record AdminRefundItemView(
        Long itemId,
        Long orderItemId,
        Long skuId,
        Integer qty,
        Long refundAmount,
        Integer restockFlag,
        LocalDateTime createdAt
) {
}
