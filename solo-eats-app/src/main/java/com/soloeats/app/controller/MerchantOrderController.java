package com.soloeats.app.controller;

import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.MerchantOrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderQueryService orderQueryService;
    private final PermissionGuard permissionGuard;

    @GetMapping
    public ApiResponse<PageResult<MerchantOrderView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        MerchantOrderQuery query = new MerchantOrderQuery(
                permissionGuard.requireMerchantId(),
                permissionGuard.resolveOptionalShopId(shopId),
                status,
                keyword,
                pageNum,
                pageSize
        );
        return ApiResponse.ok(orderQueryService.pageMerchantOrders(query));
    }
}
