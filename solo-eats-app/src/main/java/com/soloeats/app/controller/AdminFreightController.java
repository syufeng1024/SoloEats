package com.soloeats.app.controller;

import com.soloeats.app.dto.BindFreightTemplateRequest;
import com.soloeats.app.dto.SaveFreightTemplateRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.delivery.service.FreightService;
import com.soloeats.delivery.service.view.FreightTemplateView;
import jakarta.validation.Valid;
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
 * 平台侧运费模板维护。权限码由 V16 种入 t_sys_menu（id 101~102）并授予超管。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/freight-templates")
@RequiredArgsConstructor
public class AdminFreightController {

    private final FreightService freightService;

    @RequirePermission("delivery:freight:list")
    @GetMapping
    public ApiResponse<PageResult<FreightTemplateView>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(freightService.page(pageNum, pageSize));
    }

    @RequirePermission("delivery:freight:update")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "运费模板", action = "新增模板")
    @PostMapping
    public ApiResponse<FreightTemplateView> create(
            @Valid @RequestBody SaveFreightTemplateRequest request) {
        return ApiResponse.ok(freightService.saveTemplate(request.toCommand()));
    }

    @RequirePermission("delivery:freight:update")
    @OperationLog(module = "运费模板", action = "编辑模板")
    @PutMapping("/{id}")
    public ApiResponse<FreightTemplateView> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody SaveFreightTemplateRequest request) {
        return ApiResponse.ok(freightService.updateTemplate(id, request.toCommand()));
    }

    @RequirePermission("delivery:freight:update")
    @OperationLog(module = "运费模板", action = "绑定门店")
    @PostMapping("/{id}/bind-shop")
    public ApiResponse<Void> bindShop(@PathVariable("id") Long id,
                                      @Valid @RequestBody BindFreightTemplateRequest request) {
        freightService.bindShop(request.shopId(), id);
        return ApiResponse.ok();
    }
}
