package com.soloeats.settlement.service.view;

import com.soloeats.settlement.domain.entity.CommissionRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣规则视图。commissionRate 为 4 位小数比例（如 0.0600）。
 */
public record CommissionRuleView(
        Long id,
        Integer ruleScope,
        Long refId,
        BigDecimal commissionRate,
        Long minAmount,
        Long maxAmount,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer priority,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommissionRuleView of(CommissionRule rule) {
        return new CommissionRuleView(
                rule.getId(),
                rule.getRuleScope(),
                rule.getRefId(),
                rule.getCommissionRate(),
                rule.getMinAmount(),
                rule.getMaxAmount(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.getPriority(),
                rule.getStatus(),
                rule.getRemark(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
