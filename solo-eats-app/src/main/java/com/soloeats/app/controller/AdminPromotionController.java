package com.soloeats.app.controller;

import com.soloeats.app.dto.PromotionSaveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.marketing.service.PromotionAdminService;
import com.soloeats.marketing.service.view.PromotionView;
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
 * 平台满减/折扣活动后台管理。权限码由 V22 种入 t_sys_menu（id 113/114），
 * 挂在「营销中心」parent_id=6 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionAdminService promotionAdminService;

    @RequirePermission("marketing:promotion:list")
    @GetMapping
    public ApiResponse<PageResult<PromotionView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer promotionType,
            @RequestParam(required = false) Integer bearer,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(promotionAdminService.page(
                status, promotionType, bearer, keyword, pageNum, pageSize));
    }

    @RequirePermission("marketing:promotion:list")
    @GetMapping("/{id}")
    public ApiResponse<PromotionView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(promotionAdminService.detail(id));
    }

    @RequirePermission("marketing:promotion:update")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "marketing", action = "createPromotion")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody PromotionSaveRequest request) {
        return ApiResponse.ok(promotionAdminService.create(request.toCommand()));
    }

    @RequirePermission("marketing:promotion:update")
    @OperationLog(module = "marketing", action = "updatePromotion")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody PromotionSaveRequest request) {
        promotionAdminService.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @RequirePermission("marketing:promotion:update")
    @OperationLog(module = "marketing", action = "updatePromotionStatus")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody PromotionStatusRequest request) {
        promotionAdminService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }

    /** 状态变更请求体：0草稿 1进行中 2已结束 3暂停。 */
    public record PromotionStatusRequest(
            @NotNull(message = "status 不能为空")
            Integer status
    ) {
    }
}
