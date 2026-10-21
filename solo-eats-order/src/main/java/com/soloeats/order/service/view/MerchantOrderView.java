package com.soloeats.order.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家订单列表读模型。金额字段单位为分。
 */
public record MerchantOrderView(
        Long orderId,
        String orderNo,
        Long merchantId,
        Long shopId,
        Integer status,
        String statusName,
        Integer fulfillmentType,
        Long userId,
        Long goodsAmount,
        Long packagingFee,
        Long deliveryFee,
        Long platformDiscount,
        Long merchantDiscount,
        Long payAmount,
        Long refundedAmount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark,
        String pickupCode,
        LocalDateTime createdAt,
        LocalDateTime expectedDeliveryAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        List<MerchantOrderItemView> items
) {
}
