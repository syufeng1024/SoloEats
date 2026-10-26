package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.report.service.ReportService;
import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.OverviewSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 平台报表。阶段A 只开放工作台实时卡与经营总览；漏斗、排行、财务端点属于阶段B。
 * 权限码由 V23 种入 t_sys_menu（id 115~116）。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @RequirePermission("report:dashboard:view")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(reportService.dashboard());
    }

    @RequirePermission("report:overview:view")
    @GetMapping("/overview")
    public ApiResponse<OverviewSeries> overview(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(reportService.overview(dateFrom, dateTo, granularity));
    }
}
