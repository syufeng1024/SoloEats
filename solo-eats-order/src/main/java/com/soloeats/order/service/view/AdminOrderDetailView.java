package com.soloeats.order.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台订单详情读模型。收件/自取手机号均脱敏后返回，金额单位为分。
 */
public record AdminOrderDetailView(
        Long orderId,
        String orderNo,
        Long userId,
        Long merchantId,
        Long shopId,
        Integer status,
        String statusName,
        Integer fulfillmentType,
        String fulfillmentTypeName,
        Integer paymentStatus,
        Integer refundStatus,
        Integer settlementStatus,
        Integer exceptionType,
        String exceptionTypeName,
        Long goodsAmount,
        Long packagingFee,
        Long deliveryFee,
        Long discountAmount,
        Long platformDiscount,
        Long merchantDiscount,
        Long payAmount,
        Long refundedAmount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark,
        String pickupPhone,
        String pickupCode,
        LocalDateTime pickupReadyAt,
        LocalDateTime pickedUpAt,
        LocalDateTime expectedDeliveryAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AdminOrderDetailItemView> items,
        AdminOrderDetailRefundView refund,
        List<OrderTimelineView> timeline
) {
}
