package com.soloeats.settlement.service.view;

/**
 * 平台结算汇总。金额单位为分，汇总范围与账单列表使用同一组筛选条件。
 */
public record AdminSettlementSummary(
        long billCount,
        long grossAmount,
        long merchantDiscount,
        long platformDiscount,
        long commissionBase,
        long platformCommission,
        long performanceFee,
        long refundAmount,
        long payableAmount,
        long paidAmount,
        long paidCount,
        long pendingCount
) {
}
