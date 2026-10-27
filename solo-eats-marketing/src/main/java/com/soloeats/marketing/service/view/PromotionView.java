package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.domain.entity.PromotionScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台活动详情：主表字段 + 全部档位 + 全部范围项。
 * 金额单位分；discountRate 仅折扣活动有值。
 */
public record PromotionView(
        Long id,
        Integer promotionType,
        String promotionName,
        Integer bearer,
        Long merchantId,
        Long shopId,
        Integer scopeType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer stackable,
        Integer priority,
        Integer status,
        Long totalBudget,
        Long usedBudget,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RuleItem> rules,
        List<ScopeItem> scopes
) {

    public record RuleItem(
            Long id,
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
                    rule.getId(),
                    rule.getThresholdAmount() == null ? 0L : rule.getThresholdAmount(),
                    rule.getDiscountAmount(),
                    rule.getDiscountRate(),
                    rule.getMaxDiscount(),
                    rule.getSort());
        }
    }

    public record ScopeItem(
            Integer refType,
            Long refId
    ) {

        public static ScopeItem from(PromotionScope scope) {
            if (scope == null) {
                return null;
            }
            return new ScopeItem(scope.getRefType(), scope.getRefId());
        }
    }

    public static PromotionView from(Promotion promotion,
                                     List<PromotionRule> rules,
                                     List<PromotionScope> scopes) {
        if (promotion == null) {
            return null;
        }
        List<RuleItem> ruleItems = rules == null
                ? List.of()
                : rules.stream().map(RuleItem::from).filter(java.util.Objects::nonNull).toList();
        List<ScopeItem> scopeItems = scopes == null
                ? List.of()
                : scopes.stream().map(ScopeItem::from).filter(java.util.Objects::nonNull).toList();
        return new PromotionView(
                promotion.getId(),
                promotion.getPromotionType(),
                promotion.getPromotionName(),
                promotion.getBearer(),
                promotion.getMerchantId(),
                promotion.getShopId(),
                promotion.getScopeType(),
                promotion.getStartAt(),
                promotion.getEndAt(),
                promotion.getStackable(),
                promotion.getPriority(),
                promotion.getStatus(),
                promotion.getTotalBudget(),
                promotion.getUsedBudget(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt(),
                ruleItems,
                scopeItems);
    }
}
