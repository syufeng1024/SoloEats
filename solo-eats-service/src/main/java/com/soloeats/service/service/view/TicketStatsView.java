package com.soloeats.service.service.view;

import java.math.BigDecimal;

/**
 * 工单统计。solvedRate 为 0~1 四位小数，avgResponseMinutes / satisfactionAvg 两位小数。
 * 接待量本阶段只统计工单量，会话接待量待阶段B（TODO）。
 */
public record TicketStatsView(
        long ticketCount,
        BigDecimal solvedRate,
        BigDecimal avgResponseMinutes,
        BigDecimal satisfactionAvg
) {
}
