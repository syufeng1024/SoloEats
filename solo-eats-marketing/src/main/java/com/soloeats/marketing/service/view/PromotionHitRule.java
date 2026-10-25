package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.PromotionRule;

/**
 * 命中的活动档位快照，供下单链路写 t_order_discount 与排查使用。
 */
public record PromotionHitRule(
        Long ruleId,
        long thresholdAmount,
        Long discountAmount,
        Integer sort
) {

    public static PromotionHitRule from(PromotionRule rule) {
        if (rule == null) {
            return null;
        }
        long threshold = rule.getThresholdAmount() == null ? 0L : rule.getThresholdAmount();
        return new PromotionHitRule(
                rule.getId(), threshold, rule.getDiscountAmount(), rule.getSort());
    }
}
