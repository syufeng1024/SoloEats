package com.soloeats.settlement.service.view;

import com.soloeats.settlement.domain.entity.CommissionRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣明细视图。baseAmount * commissionRate 按 HALF_UP 取整后必须等于 commissionAmount，
 * 用于「任意时刻可用订单数据复算出同一金额」的核对。
 */
public record CommissionRecordView(
        Long id,
        Long orderId,
        Long merchantId,
        Long shopId,
        Long categoryId,
        Long baseAmount,
        Long ruleId,
        Integer ruleScope,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long settlementBillId,
        LocalDateTime createdAt
) {

    public static CommissionRecordView of(CommissionRecord record) {
        return new CommissionRecordView(
                record.getId(),
                record.getOrderId(),
                record.getMerchantId(),
                record.getShopId(),
                record.getCategoryId(),
                record.getBaseAmount(),
                record.getRuleId(),
                record.getRuleScope(),
                record.getCommissionRate(),
                record.getCommissionAmount(),
                record.getSettlementBillId(),
                record.getCreatedAt()
        );
    }
}
