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
 */
public record MarketingCalculateCommand(
        Long userId,
        Long shopId,
        Long goodsAmount,
        List<MarketingItem> items,
        Long couponDiscount,
        LocalDateTime at
) {

    public record MarketingItem(
            Long skuId,
            Integer quantity,
            Long payableAmount
    ) {
    }
}
