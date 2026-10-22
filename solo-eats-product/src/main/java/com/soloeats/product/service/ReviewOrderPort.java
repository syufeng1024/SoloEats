package com.soloeats.product.service;

import java.time.LocalDateTime;

/**
 * 评价资格所需的订单快照。
 *
 * <p>product 模块不依赖 order 模块实体：由 app 模块用 OrderMapper/OrderItemMapper
 * 实现本端口，把「订单归属 + 明细归属 + 完成时间」以只读快照传入。
 */
public interface ReviewOrderPort {

    /**
     * 订单或明细不存在、或明细不属于该订单时返回 null。
     */
    OrderItemSnapshot find(Long orderId, Long orderItemId);

    record OrderItemSnapshot(
            Long orderId,
            Long orderItemId,
            Long userId,
            Long shopId,
            Long spuId,
            Long skuId,
            Integer orderStatus,
            LocalDateTime completedAt
    ) {
    }
}
