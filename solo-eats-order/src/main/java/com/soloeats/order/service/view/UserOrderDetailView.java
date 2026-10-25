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
        /** 该订单对应配送单 id（t_delivery_order.id），无配送单为 null。 */
        Long deliveryId,
        /** 配送单状态：0 待派单 / 10 已派单 / 20 已接单 / 30 已取货 / 40 配送中 / 50 已送达 / 70 已取消 / 71 已改派；无配送单为 null。 */
        Integer deliveryStatus,
        List<UserOrderItemView> items,
        List<OrderTimelineView> timeline
) {
}
