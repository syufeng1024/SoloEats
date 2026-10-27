package com.soloeats.settlement.service.view;

import com.soloeats.settlement.domain.entity.CommissionRecord;

import java.math.BigDecimal;

/**
 * 规则命中试算结果，也是抽佣落库后的计算结果。
 *
 * <p>ruleId / ruleScope 为 null 表示未命中任何规则，使用内置默认费率
 * （app.settlement.default-commission-rate，默认 0.0600）；
 * commissionAmount = HALF_UP(baseAmount * commissionRate)，单位为分。
 */
public record CommissionResolveResult(
        Long ruleId,
        Integer ruleScope,
        BigDecimal commissionRate,
        Long baseAmount,
        Long commissionAmount
) {

    /**
     * 是否回落到内置默认费率。
     */
    public boolean usingDefaultRate() {
        return ruleId == null;
    }

    public static CommissionResolveResult of(CommissionRecord record) {
        return new CommissionResolveResult(
                record.getRuleId(),
                record.getRuleScope(),
                record.getCommissionRate(),
                record.getBaseAmount(),
                record.getCommissionAmount()
        );
    }
}
