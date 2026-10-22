package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.product.service.SoldOutService;
import com.soloeats.product.service.view.ReasonStatView;
import com.soloeats.product.service.view.SoldOutRecordView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/sold-out")
@RequiredArgsConstructor
public class AdminSoldOutController {

    private final SoldOutService soldOutService;

    @RequirePermission("product:soldout:list")
    @GetMapping
    public ApiResponse<PageResult<SoldOutRecordView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(soldOutService.adminPage(
                shopId, skuId, reason, start, end, pageNum, pageSize));
    }

    @RequirePermission("product:soldout:list")
    @GetMapping("/reason-stats")
    public ApiResponse<List<ReasonStatView>> reasonStats(
            @RequestParam Long shopId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return ApiResponse.ok(soldOutService.reasonStats(shopId, start, end));
    }
}
