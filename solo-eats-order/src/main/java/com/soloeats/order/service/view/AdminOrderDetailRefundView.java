package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * 平台订单详情关联的最近一笔退款单摘要，金额单位为分。
 */
public record AdminOrderDetailRefundView(
        Long refundId,
        String refundNo,
        Integer refundType,
        String refundTypeName,
        Long amount,
        Integer status,
        String statusName,
        String reason,
        LocalDateTime merchantReviewAt,
        LocalDateTime successAt,
        LocalDateTime createdAt
) {
}
