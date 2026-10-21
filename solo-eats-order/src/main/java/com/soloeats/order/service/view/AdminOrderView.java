package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * 平台订单监控读模型。收件手机号在管理端脱敏。
 */
public record AdminOrderView(
        Long orderId,
        String orderNo,
        Long merchantId,
        Long shopId,
        Long userId,
        Integer status,
        String statusName,
        Integer fulfillmentType,
        Integer paymentStatus,
        Integer refundStatus,
        Integer settlementStatus,
        Long payAmount,
        Long refundedAmount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        LocalDateTime createdAt,
        LocalDateTime expectedDeliveryAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        String cancelReason
) {
}
