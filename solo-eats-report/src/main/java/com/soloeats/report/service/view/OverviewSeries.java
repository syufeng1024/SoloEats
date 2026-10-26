package com.soloeats.report.service.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 经营总览序列。缺失日期补 0；费率为 0~1 小数，保留 4 位。
 *
 * @param granularity day / week / month
 */
public record OverviewSeries(
        LocalDate dateFrom,
        LocalDate dateTo,
        String granularity,
        List<Point> points
) {

    /**
     * @param period           day: 2026-10-26；week: 2026-W43；month: 2026-10
     * @param periodStart      自然周期起点（周为周一，月为 1 日）
     * @param periodEnd        自然周期终点
     * @param avgOrderAmount   客单价，分；orderCount = 0 时为 0
     * @param refundRate       退款成功单数 / 支付成功单数
     * @param repurchaseRate   下单 >= 2 次用户数 / 下单用户数
     */
    public record Point(
            String period,
            LocalDate periodStart,
            LocalDate periodEnd,
            long gmv,
            long orderCount,
            long avgOrderAmount,
            long refundAmount,
            int refundOrderCount,
            BigDecimal refundRate,
            BigDecimal repurchaseRate
    ) {
    }
}
