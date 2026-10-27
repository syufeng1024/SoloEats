package com.soloeats.app.controller;

import com.soloeats.app.dto.ReportRebuildRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.report.service.ReportService;
import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.FinanceReportView;
import com.soloeats.report.service.view.FunnelView;
import com.soloeats.report.service.view.OverviewSeries;
import com.soloeats.report.service.view.ProductRankView;
import com.soloeats.report.service.view.RiderAnalysisView;
import com.soloeats.report.service.view.ShopRankView;
import com.soloeats.report.service.view.UserReportView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台报表。工作台与经营总览读预聚合表；漏斗、排行、财务、用户与重算属于阶段B。
 * 权限码由 V23（115~116）与 V30（150~156）种入 t_sys_menu。
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

    @RequirePermission("report:funnel:view")
    @GetMapping("/funnel")
    public ApiResponse<FunnelView> funnel(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(reportService.funnel(dateFrom, dateTo));
    }

    @RequirePermission("report:product:view")
    @GetMapping("/products")
    public ApiResponse<PageResult<ProductRankView>> products(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "saleAmount") String sortBy,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(reportService.products(
                dateFrom, dateTo, sortBy, categoryId, pageNum, pageSize));
    }

    @RequirePermission("report:shop:view")
    @GetMapping("/shops")
    public ApiResponse<List<ShopRankView>> shops(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "10") int topN) {
        return ApiResponse.ok(reportService.shops(dateFrom, dateTo, topN));
    }

    @RequirePermission("report:rider:view")
    @GetMapping("/riders")
    public ApiResponse<List<RiderAnalysisView>> riders(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "10") int topN) {
        return ApiResponse.ok(reportService.riders(dateFrom, dateTo, topN));
    }

    @RequirePermission("report:finance:view")
    @GetMapping("/finance")
    public ApiResponse<FinanceReportView> finance(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(reportService.finance(dateFrom, dateTo));
    }

    @RequirePermission("report:user:view")
    @GetMapping("/users")
    public ApiResponse<UserReportView> users(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(reportService.users(dateFrom, dateTo));
    }

    @RequirePermission("report:rebuild")
    @PostMapping("/rebuild")
    public ApiResponse<Integer> rebuild(@Valid @RequestBody ReportRebuildRequest request) {
        return ApiResponse.ok(reportService.rebuild(request.date()));
    }
}
