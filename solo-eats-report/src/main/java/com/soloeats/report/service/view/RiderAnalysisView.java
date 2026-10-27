package com.soloeats.report.service.view;

import java.math.BigDecimal;

/**
 * 骑手分析行。onTimeRate 为百分比数值（66.67 表示 66.67%），分母为送达单量。
 */
public record RiderAnalysisView(
        Long riderId,
        String riderName,
        long deliveryCount,
        long onTimeCount,
        BigDecimal onTimeRate,
        BigDecimal rating,
        long incomeAmount,
        long onlineMinutes,
        int avgDeliveryMinutes
) {
}
