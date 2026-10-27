package com.soloeats.order.service.view;

import java.util.List;

/**
 * 平台售后详情：退款主单 + t_refund_item 明细。
 */
public record AdminRefundDetailView(
        AdminRefundView refund,
        List<AdminRefundItemView> items
) {
}
