package com.soloeats.app.controller;

import com.soloeats.app.mapper.RiderSelfQueryMapper;
import com.soloeats.app.mapper.RiderAttendanceRow;
import com.soloeats.app.mapper.RiderStatsRow;
import com.soloeats.app.vo.RiderAttendanceDayView;
import com.soloeats.app.vo.RiderStatsView;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 骑手端「我的评分 / 考勤」。riderId 由登录账号反查 t_rider，不读请求参数。
 */
@RequireRole(Roles.RIDER)
@RestController
@RequestMapping("/api/v1/rider")
@RequiredArgsConstructor
public class RiderProfileController {

    /** 未传日期区间时默认返回最近 7 天（含今天）。 */
    private static final int DEFAULT_ATTENDANCE_DAYS = 7;

    private final RiderService riderService;
    private final RiderSelfQueryMapper riderProfileQueryMapper;

    @GetMapping("/stats")
    public ApiResponse<RiderStatsView> stats() {
        RiderStatsRow row = riderProfileQueryMapper.selectStats(currentRiderId());
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手档案不存在");
        }
        return ApiResponse.ok(new RiderStatsView(
                row.getRating(), row.getOnTimeRate(), row.getTotalOrders(), row.getCertStatus()));
    }

    /**
     * 按日汇总在线次数与在线分钟数。
     * dateTo 为闭区间；minutes 是当日首末上线时间的差值，属于估算值，不剔除中途下线空档。
     */
    @GetMapping("/attendance")
    public ApiResponse<List<RiderAttendanceDayView>> attendance(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        LocalDate end = dateTo == null ? LocalDate.now() : dateTo;
        LocalDate start = dateFrom == null
                ? end.minusDays(DEFAULT_ATTENDANCE_DAYS - 1L) : dateFrom;
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        List<RiderAttendanceRow> rows = riderProfileQueryMapper.listAttendance(
                currentRiderId(), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return ApiResponse.ok(rows.stream()
                .map(row -> new RiderAttendanceDayView(
                        row.getWorkDate(), row.getOnlineTimes(), row.getOnlineMinutes()))
                .toList());
    }

    private Long currentRiderId() {
        UserPrincipal principal = AuthContext.require();
        return riderService.resolveRiderId(principal.userId(), principal.riderId());
    }
}
