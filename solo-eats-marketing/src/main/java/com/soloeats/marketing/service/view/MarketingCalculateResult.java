package com.soloeats.marketing.service.view;

import java.util.List;

/**
 * 营销试算结果。未命中任何活动时 promotionId/promotionName 为 null、discountAmount 为 0、
 * apportion 与 hitRules 为空列表，不抛异常。
 *
 * @param discountAmount 本次满减抵扣（分），已按商品小计封顶
 * @param payableAmount  保底实付（分），不小于 1
 * @param apportion      满减在各商品行上的分摊，Σ 分摊 = discountAmount
 */
public record MarketingCalculateResult(
        Long promotionId,
        String promotionName,
        long discountAmount,
        long payableAmount,
        List<DiscountApportionItem> apportion,
        List<PromotionHitRule> hitRules
) {
}
