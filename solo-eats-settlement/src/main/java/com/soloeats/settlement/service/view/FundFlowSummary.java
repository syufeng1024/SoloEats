package com.soloeats.settlement.service.view;

/**
 * 资金流水汇总，为 T11 财务报表供数。金额单位分。
 *
 * <p>利润口径（口径裁定，不随任务卡变动）：
 * {@code profit = commissionIncome - settlementExpense - subsidyCost - payoutExpense}。
 * discountCost 与 refundOffset 只做展示，不参与 profit 计算。
 */
public record FundFlowSummary(
        Long commissionIncome,
        Long settlementExpense,
        Long subsidyCost,
        Long discountCost,
        Long payoutExpense,
        Long refundOffset,
        Long profit
) {

    /**
     * 按各分项构造汇总，profit 由分项推导，避免多处重复计算。
     */
    public static FundFlowSummary of(long commissionIncome, long settlementExpense,
                                     long subsidyCost, long discountCost,
                                     long payoutExpense, long refundOffset) {
        long profit = commissionIncome - settlementExpense - subsidyCost - payoutExpense;
        return new FundFlowSummary(commissionIncome, settlementExpense, subsidyCost,
                discountCost, payoutExpense, refundOffset, profit);
    }

    /**
     * 空汇总，无流水时返回全 0，避免前端拿到 null。
     */
    public static FundFlowSummary empty() {
        return of(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
