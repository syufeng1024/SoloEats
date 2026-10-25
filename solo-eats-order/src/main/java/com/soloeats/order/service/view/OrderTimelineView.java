package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * C 端订单时间轴节点，按 created_at 升序返回。
 */
public record OrderTimelineView(
        Integer fromStatus,
        String fromStatusName,
        Integer toStatus,
        String toStatusName,
        String event,
        Integer operatorType,
        LocalDateTime createdAt
) {
}
