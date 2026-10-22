package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.service.SysLogService;
import com.soloeats.system.service.view.LoginLogView;
import com.soloeats.system.service.view.OperLogView;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 操作日志 / 登录日志查询。
 *
 * <p>本控制器路径命中切面的排除前缀 {@code /api/v1/admin/logs/}，查询本身不会产生操作日志。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogService sysLogService;

    @RequirePermission("system:log:oper:list")
    @GetMapping("/oper")
    public ApiResponse<PageResult<OperLogView>> oper(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) Integer resultCode,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysLogService.pageOper(
                module,
                operatorId,
                resultCode,
                parseDate(dateFrom, "dateFrom", false),
                parseDate(dateTo, "dateTo", true),
                pageNum,
                pageSize));
    }

    @RequirePermission("system:log:login:list")
    @GetMapping("/login")
    public ApiResponse<PageResult<LoginLogView>> login(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysLogService.pageLogin(
                username,
                success,
                parseDate(dateFrom, "dateFrom", false),
                parseDate(dateTo, "dateTo", true),
                pageNum,
                pageSize));
    }

    private LocalDateTime parseDate(String raw, String field, boolean endOfDay) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试日期格式
        }
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return endOfDay
                    ? date.atTime(23, 59, 59, 999_000_000)
                    : date.atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }
}
