package com.soloeats.report.service.view;

import java.math.BigDecimal;

/**
 * 用户报表。复购率 = 区间内下单 >= 2 次用户 / 区间内下单用户，百分比数值。
 */
public record UserReportView(
        long newUserCount,
        long activeUserCount,
        long repurchaseUserCount,
        BigDecimal repurchaseRate
) {
}
