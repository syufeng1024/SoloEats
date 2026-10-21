package com.soloeats.order.service.view;

/**
 * 监控汇总与订单列表使用同一组筛选条件，金额单位为分。
 */
public record AdminOrderSummary(
        long total,
        long waitAccept,
        long preparing,
        long waitRiderPickup,
        long waitUserPickup,
        long delivering,
        long completed,
        long cancelled,
        long paidAmount,
        long refundedAmount
) {
}
