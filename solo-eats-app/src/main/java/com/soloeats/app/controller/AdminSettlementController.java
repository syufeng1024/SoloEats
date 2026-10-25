package com.soloeats.app.controller;

import com.soloeats.app.dto.BillAuditRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.AdminSettlementStatsResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @RequirePermission("settlement:bill:audit")
    @OperationLog(module = "settlement", action = "auditBill")
    @PostMapping("/{billId}/audit")
    public ApiResponse<Void> audit(@PathVariable("billId") Long billId,
                                   @Valid @RequestBody BillAuditRequest request) {
        settlementService.auditBill(billId, request.pass(), request.remark(),
                AuthContext.require().userId());
        return ApiResponse.ok();
    }

    @RequirePermission("settlement:bill:pay")
    @OperationLog(module = "settlement", action = "payBill")
    @PostMapping("/{billId}/pay")
    public ApiResponse<Void> pay(@PathVariable("billId") Long billId) {
        settlementService.payBill(billId, AuthContext.require().userId());
        return ApiResponse.ok();
    }
}
