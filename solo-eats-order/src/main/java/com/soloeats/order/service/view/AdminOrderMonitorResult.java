package com.soloeats.order.service.view;

import java.util.List;

public record AdminOrderMonitorResult(
        List<AdminOrderView> orders,
        long total,
        long pageNum,
        long pageSize,
        AdminOrderSummary summary
) {
}
