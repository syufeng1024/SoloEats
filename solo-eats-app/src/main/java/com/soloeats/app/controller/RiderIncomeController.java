package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.delivery.service.RiderIncomeService;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.delivery.service.view.RiderIncomeDetailView;
import com.soloeats.delivery.service.view.RiderIncomeOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 骑手端收入。riderId 由登录账号 userId 反查 t_rider，从不从请求参数读取。
 */
@RequireRole(Roles.RIDER)
@RestController
@RequestMapping("/api/v1/rider/income")
@RequiredArgsConstructor
public class RiderIncomeController {

    private final RiderIncomeService riderIncomeService;
    private final RiderService riderService;

    @GetMapping("/overview")
    public ApiResponse<RiderIncomeOverview> overview() {
        return ApiResponse.ok(riderIncomeService.overview(currentRiderId()));
    }

    @GetMapping("/details")
    public ApiResponse<PageResult<RiderIncomeDetailView>> details(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(riderIncomeService.details(
                currentRiderId(), dateFrom, dateTo, pageNum, pageSize));
    }

    private Long currentRiderId() {
        UserPrincipal principal = AuthContext.require();
        return riderService.resolveRiderId(principal.userId(), principal.riderId());
    }
}
