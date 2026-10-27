package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * 平台售后列表读模型。orderStatus 取自关联订单，订单缺失时为 null。
 */
public record AdminRefundView(
        Long refundId,
        String refundNo,
        Long orderId,
        String orderNo,
        Integer orderStatus,
        String orderStatusName,
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
