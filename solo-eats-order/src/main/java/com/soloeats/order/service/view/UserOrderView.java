package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * C 端订单列表读模型。金额字段单位为分。
 */
public record UserOrderView(
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
        LocalDateTime createdAt
) {
}
