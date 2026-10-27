package com.soloeats.app.vo;

import java.time.LocalDate;

/**
 * 单日考勤汇总。
 * onlineMinutes 为首条上线与末条上线的差值，只能作为在网时长估算，
 * 不包含中途掉线后未重新上线的空档，也不扣除同时段下线记录。
 */
public record RiderAttendanceDayView(
        LocalDate date,
        Long onlineTimes,
        Long onlineMinutes
) {
}
