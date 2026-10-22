package com.soloeats.store.service.command;

/**
 * 单个营业时段，时间为 HH:mm。endTime 早于 startTime 时自动按跨天处理。
 */
public record SaveBusinessHourCommand(
        Integer dayOfWeek,
        String startTime,
        String endTime
) {
}
