package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/monitor")
    public ApiResponse<AdminOrderMonitorResult> monitor(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        AdminOrderQuery query = new AdminOrderQuery(
                merchantId, shopId, status, keyword, dateFrom, dateTo, pageNum, pageSize
        );
        return ApiResponse.ok(orderQueryService.monitor(query));
    }
}
