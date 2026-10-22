package com.soloeats.app.controller;

import com.soloeats.app.dto.ReviewStatusRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.ProductReviewService;
import com.soloeats.product.service.view.ReviewView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ProductReviewService productReviewService;

    @RequirePermission("product:review:list")
    @GetMapping
    public ApiResponse<PageResult<ReviewView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long spuId,
            @RequestParam(required = false) Long riderId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer scoreLevel,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(productReviewService.adminPage(
                shopId, spuId, riderId, status, scoreLevel, pageNum, pageSize));
    }

    @RequirePermission("product:review:audit")
    @OperationLog(module = "评价管理", action = "审核评价")
    @PutMapping("/{id}/status")
    public ApiResponse<ReviewView> audit(@PathVariable Long id,
                                         @Valid @RequestBody ReviewStatusRequest request) {
        return ApiResponse.ok(productReviewService.audit(
                id, request.status(), request.topFlag()));
    }
}
