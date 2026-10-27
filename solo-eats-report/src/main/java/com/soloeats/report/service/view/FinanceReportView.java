package com.soloeats.report.service.view;

import java.math.BigDecimal;

/**
 * 财务报表。金额单位为分；refundRate 为百分比数值（分母为支付成功单数）。
 * 毛利润 = 佣金收入 - 补贴成本 - 优惠成本，可为负。
 */
public record FinanceReportView(
        long commissionIncome,
        long settlementExpense,
        long subsidyCost,
        long discountCost,
        long withdrawExpense,
        long refundOffset,
        long grossProfit,
        BigDecimal refundRate
) {
}
