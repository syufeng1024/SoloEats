package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.view.FundFlowSummary;
import com.soloeats.settlement.service.view.FundFlowView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 平台资金流水：明细分页与汇总（为 T11 财务报表供数）。
 * 权限码由 V27 种入 t_sys_menu（id 145），挂在「资金中心」parent_id = 8 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/fund-flows")
@RequiredArgsConstructor
public class AdminFundFlowController {

    private final FundFlowService fundFlowService;

    /**
     * flowType：1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵。
     */
    @RequirePermission("settlement:fund:list")
    @GetMapping
    public ApiResponse<PageResult<FundFlowView>> list(
            @RequestParam(required = false) Integer flowType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(fundFlowService.page(flowType, dateFrom, dateTo, pageNum, pageSize));
    }

    /**
     * 汇总：profit = commissionIncome − settlementExpense − subsidyCost − payoutExpense。
     */
    @RequirePermission("settlement:fund:list")
    @GetMapping("/summary")
    public ApiResponse<FundFlowSummary> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(fundFlowService.summary(dateFrom, dateTo));
    }
}
