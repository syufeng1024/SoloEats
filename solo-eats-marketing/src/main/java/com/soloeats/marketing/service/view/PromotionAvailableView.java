package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端可见活动。只暴露用户需要看到的字段：不含 total_budget / used_budget /
 * priority / status / scope_json 等后台或内部字段。
 *
 * @param rules 该活动的全部档位，按 sort 升序；门槛与金额单位均为分
 */
public record PromotionAvailableView(
        Long id,
        String promotionName,
        Integer promotionType,
        Integer bearer,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<RuleItem> rules
) {

    public record RuleItem(
            long thresholdAmount,
            Long discountAmount,
            BigDecimal discountRate,
            Long maxDiscount,
            Integer sort
    ) {

        public static RuleItem from(PromotionRule rule) {
            if (rule == null) {
                return null;
            }
            return new RuleItem(
                    rule.getThresholdAmount() == null ? 0L : rule.getThresholdAmount(),
                    rule.getDiscountAmount(),
                    rule.getDiscountRate(),
                    rule.getMaxDiscount(),
                    rule.getSort());
        }
    }

    public static PromotionAvailableView from(Promotion promotion, List<PromotionRule> rules) {
        if (promotion == null) {
            return null;
        }
        List<RuleItem> items = rules == null
                ? List.of()
                : rules.stream().map(RuleItem::from).filter(java.util.Objects::nonNull).toList();
        return new PromotionAvailableView(
                promotion.getId(),
                promotion.getPromotionName(),
                promotion.getPromotionType(),
                promotion.getBearer(),
                promotion.getStartAt(),
                promotion.getEndAt(),
                items);
    }
}
