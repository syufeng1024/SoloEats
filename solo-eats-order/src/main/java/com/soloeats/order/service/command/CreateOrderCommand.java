package com.soloeats.order.service.command;

import java.util.List;

/**
 * 下单命令。Controller DTO 在 app 模块，避免 order 模块反向依赖 app。
 */
public record CreateOrderCommand(
        Long userId,
        Long merchantId,
        Long shopId,
        Integer fulfillmentType,
        Long couponId,
        Long packagingFee,
        Long deliveryFee,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String pickupPhone,
        String remark,
        List<ItemCommand> items
) {

    public record ItemCommand(Long skuId, Integer quantity) {
    }
}
