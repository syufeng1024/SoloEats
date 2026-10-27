package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.order.service.AdminOrderReadService;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.AdminExceptionQuery;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.view.AdminExceptionOrderView;
import com.soloeats.order.service.view.AdminOrderDetailView;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AdminOrderReadService adminOrderReadService;

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

    /**
     * 异常订单池：t_order.exception_type != 0。只读，不做处理动作。
     */
    @GetMapping("/exceptions")
    public ApiResponse<PageResult<AdminExceptionOrderView>> exceptions(
            @RequestParam(required = false) Integer exceptionType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        AdminExceptionQuery query = new AdminExceptionQuery(
                exceptionType, status, merchantId, shopId, keyword,
                dateFrom, dateTo, pageNum, pageSize
        );
        return ApiResponse.ok(adminOrderReadService.pageExceptionOrders(query));
    }

    /**
     * 平台订单详情。不校验归属，平台超管可见全量。
     */
    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderDetailView> detail(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(adminOrderReadService.getOrderDetail(orderId));
    }
}
