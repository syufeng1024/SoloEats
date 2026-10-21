package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
