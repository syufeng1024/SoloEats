package com.soloeats.app.controller;

import com.soloeats.app.dto.CouponGrantRequest;
import com.soloeats.app.dto.CouponTemplateSaveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.marketing.service.CouponAdminService;
import com.soloeats.marketing.service.view.CouponGrantRecordView;
import com.soloeats.marketing.service.view.CouponTemplateView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台券后台管理：模板 CRUD、启停、定向发券、领取记录。权限码由 V24 种入
 * t_sys_menu（id 117~119），挂在「营销中心」parent_id=6 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponAdminService couponAdminService;

    @RequirePermission("marketing:coupon:list")
    @GetMapping
    public ApiResponse<PageResult<CouponTemplateView>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer couponType,
            @RequestParam(required = false) Integer bearer,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(couponAdminService.page(
                name, status, couponType, bearer, pageNum, pageSize));
    }

    @RequirePermission("marketing:coupon:list")
    @GetMapping("/{id}")
    public ApiResponse<CouponTemplateView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(couponAdminService.detail(id));
    }

    @RequirePermission("marketing:coupon:save")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "createCoupon")
    @PostMapping
    public ApiResponse<CouponTemplateView> create(
            @Valid @RequestBody CouponTemplateSaveRequest request) {
        return ApiResponse.ok(couponAdminService.create(request.toCommand()));
    }

    @RequirePermission("marketing:coupon:save")
    @OperationLog(module = "marketing", action = "updateCoupon")
    @PutMapping("/{id}")
    public ApiResponse<CouponTemplateView> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CouponTemplateSaveRequest request) {
        return ApiResponse.ok(couponAdminService.update(id, request.toCommand()));
    }

    @RequirePermission("marketing:coupon:save")
    @OperationLog(module = "marketing", action = "updateCouponStatus")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody CouponStatusRequest request) {
        couponAdminService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @RequirePermission("marketing:coupon:grant")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "grantCoupon")
    @PostMapping("/{id}/grant")
    public ApiResponse<Integer> grant(@PathVariable("id") Long id,
                                      @Valid @RequestBody CouponGrantRequest request) {
        return ApiResponse.ok(couponAdminService.grant(id, request.userIds()));
    }

    @RequirePermission("marketing:coupon:list")
    @GetMapping("/{id}/records")
    public ApiResponse<PageResult<CouponGrantRecordView>> records(
            @PathVariable("id") Long id,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(couponAdminService.records(id, status, pageNum, pageSize));
    }

    /** 启停请求体。status：0 启用、1 停用（t_coupon_template 既有取值的反直觉定义）。 */
    public record CouponStatusRequest(
            @NotNull(message = "status 不能为空")
            Integer status
    ) {
    }
}
