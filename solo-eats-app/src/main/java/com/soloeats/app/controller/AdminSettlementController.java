package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.AdminSettlementStatsResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    @PostMapping("/generate")
    public ApiResponse<SettlementGenerateResult> generate() {
        return ApiResponse.ok(settlementService.generateDailyBills());
    }

    @GetMapping("/stats")
    public ApiResponse<AdminSettlementStatsResult> stats(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(settlementService.adminStats(
                merchantId, status, dateFrom, dateTo, pageNum, pageSize
        ));
    }
}
