package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.MarketingCalculateResult;
import com.soloeats.marketing.service.view.PromotionAvailableView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 满减/折扣活动试算与 C 端可见活动查询。order 模块通过 calculate 拿结果，
 * 自己写 t_order_discount；marketing 不依赖 order。
 */
public interface PromotionService {

    MarketingCalculateResult calculate(MarketingCalculateCommand command);

    /**
     * C 端可见活动：进行中 + 命中本次门店/商品范围的活动，按 priority DESC、id ASC 返回。
     *
     * @param shopId 门店 ID，可空；为空时指定门店范围的活动不参与
     * @param spuIds 本次浏览/下单的 SPU 集合，可空；为空时品类/商品范围的活动无法判定，直接跳过
     * @param at     查询时间，空则取当前时间
     */
    List<PromotionAvailableView> listAvailable(Long shopId, List<Long> spuIds, LocalDateTime at);
}
