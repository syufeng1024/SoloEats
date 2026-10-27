package com.soloeats.marketing.service;

/**
 * 秒杀预占结果。
 *
 * @param orderId       t_seckill_order.id
 * @param seckillPrice  秒杀价，分
 * @param reserveToken  预占凭证，格式 {@code "SK" + orderId}；下单链路凭此核销，无需额外存储
 */
public record SeckillReserveResult(
        Long orderId,
        Long itemId,
        int qty,
        long seckillPrice,
        String reserveToken
) {
}
