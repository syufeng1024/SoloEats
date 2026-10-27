package com.soloeats.marketing.service.command;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀场次新建/编辑入参。
 *
 * <p>status 不在入参内：由 session_start/session_end 与当前时间推导（0待开始 1进行中 2已结束），
 * 新建时写入快照，C 端读取时按时间窗重新推导。
 *
 * @param items 场次商品，非空；同一 skuId 不允许重复（表上 uk_activity_sku 兜底）
 */
public record SeckillActivitySaveCommand(
        String activityName,
        LocalDateTime sessionStart,
        LocalDateTime sessionEnd,
        LocalDateTime preheatAt,
        List<ItemCommand> items
) {

    /**
     * 商品入参。金额单位分；seckillStock 是独立库存，不占用 t_sku_stock。
     */
    public record ItemCommand(
            Long shopId,
            Long spuId,
            Long skuId,
            Long seckillPrice,
            Integer seckillStock,
            Integer limitPerUser
    ) {
    }
}
