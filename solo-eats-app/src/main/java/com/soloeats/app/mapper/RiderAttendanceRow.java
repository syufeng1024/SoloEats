package com.soloeats.app.mapper;

import lombok.Data;

import java.time.LocalDate;

/** t_rider_online_log 按日聚合结果。 */
@Data
public class RiderAttendanceRow {

    private LocalDate workDate;

    private Long onlineTimes;

    /** 首条上线与末条上线的分钟差，仅作估算。 */
    private Long onlineMinutes;
}
