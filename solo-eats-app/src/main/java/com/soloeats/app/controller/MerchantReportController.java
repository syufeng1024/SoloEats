package com.soloeats.app.controller;

import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.view.MerchantDashboardView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家工作台。只返回 t_order 可直接计数的指标，营业额与趋势聚合归 T11 报表模块。
 */
@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/report")
@RequiredArgsConstructor
public class MerchantReportController {

    private final OrderQueryService orderQueryService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/dashboard")
    public ApiResponse<MerchantDashboardView> dashboard() {
        return ApiResponse.ok(orderQueryService.dashboard(
                permissionGuard.requireMerchantId(),
                permissionGuard.principal().shopId()
        ));
    }
}
