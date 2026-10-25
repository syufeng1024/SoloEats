package com.soloeats.order.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端订单详情读模型。金额字段单位为分；receiverPhoneMask 为脱敏号码，禁止返回明文。
 */
public record UserOrderDetailView(
        Long orderId,
        String orderNo,
        Long shopId,
        Integer status,
        String statusName,
        Integer fulfillmentType,
        String fulfillmentTypeName,
        Long goodsAmount,
        Long packagingFee,
        Long deliveryFee,
        Long payAmount,
        Integer itemCount,
        String pickupCode,
        LocalDateTime createdAt,
        String receiverName,
        String receiverPhoneMask,
        String receiverAddress,
        String remark,
        Integer refundStatus,
        Long refundedAmount,
        List<UserOrderItemView> items,
        List<OrderTimelineView> timeline
) {
}
