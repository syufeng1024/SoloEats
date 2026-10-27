package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.order.service.AdminOrderReadService;
import com.soloeats.order.service.query.AdminRefundQuery;
import com.soloeats.order.service.view.AdminRefundDetailView;
import com.soloeats.order.service.view.AdminRefundView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 平台售后只读查询。审核动作不属本卡，这里不暴露任何写接口。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final AdminOrderReadService adminOrderReadService;

    @GetMapping
    public ApiResponse<PageResult<AdminRefundView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        AdminRefundQuery query = new AdminRefundQuery(
                status, keyword, dateFrom, dateTo, pageNum, pageSize
        );
        return ApiResponse.ok(adminOrderReadService.pageRefunds(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminRefundDetailView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminOrderReadService.getRefundDetail(id));
    }
}
