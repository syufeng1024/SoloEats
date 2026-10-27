package com.soloeats.marketing.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销试算入参。
 *
 * @param goodsAmount  折后商品小计（分），满减门槛只按该金额判定，不含配送费与打包费
 * @param items        商品明细；payableAmount 为该行折后金额（分）
 * @param couponDiscount 券模块已算好的券抵扣（分），可空；仅用于互斥判断与实付计算
 * @param at           试算时间，空则取当前时间
 * @param merchantId   商家业务 ID（t_merchant.id），可空；仅 scopeType=2 的商家满减需要，
 *                     为 null 时商家范围活动直接跳过（无法判定归属）
 */
public record MarketingCalculateCommand(
        Long userId,
        Long shopId,
        Long goodsAmount,
        List<MarketingItem> items,
        Long couponDiscount,
        LocalDateTime at,
        Long merchantId
) {

    /**
     * 兼容阶段A-0 的 6 参构造：既有调用方（MarketingClientController、试算单测）不改代码即可编译，
     * merchantId 缺省为 null，此时商家范围活动不参与试算。
     */
    public MarketingCalculateCommand(Long userId, Long shopId, Long goodsAmount,
                                     List<MarketingItem> items, Long couponDiscount, LocalDateTime at) {
        this(userId, shopId, goodsAmount, items, couponDiscount, at, null);
    }

    public record MarketingItem(
            Long skuId,
            Integer quantity,
            Long payableAmount
    ) {
    }
}
