package com.soloeats.order.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家端订单详情读模型。金额字段单位为分；receiverPhoneMask 为脱敏号码，禁止返回明文。
 * t_order 无 paid_at 列，paidAt 固定为 null，不新增列。
 */
public record MerchantOrderDetailView(
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
        String receiverName,
        String receiverPhoneMask,
        String receiverAddress,
        String remark,
        String pickupCode,
        LocalDateTime pickupReadyAt,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<UserOrderItemView> items,
        List<OrderTimelineView> timeline
) {
}
