package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveSpuRequest;
import com.soloeats.app.dto.UpdateSpuStatusRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.SpuService;
import com.soloeats.product.service.command.SaveSpuCommand;
import com.soloeats.product.service.view.SpuView;
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

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/spus")
@RequiredArgsConstructor
public class AdminSpuController {

    private final SpuService spuService;

    @RequirePermission("product:spu:list")
    @GetMapping
    public ApiResponse<PageResult<SpuView>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(spuService.adminPage(categoryId, status, keyword, pageNum, pageSize));
    }

    @RequirePermission("product:spu:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "商品管理", action = "新增商品")
    @PostMapping
    public ApiResponse<SpuView> create(@Valid @RequestBody SaveSpuRequest request) {
        return ApiResponse.ok(spuService.create(toCommand(request)));
    }

    @RequirePermission("product:spu:update")
    @OperationLog(module = "商品管理", action = "编辑商品")
    @PutMapping("/{id}")
    public ApiResponse<SpuView> update(@PathVariable Long id,
                                       @Valid @RequestBody SaveSpuRequest request) {
        return ApiResponse.ok(spuService.update(id, toCommand(request)));
    }

    @RequirePermission("product:spu:update")
    @OperationLog(module = "商品管理", action = "变更商品状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateSpuStatusRequest request) {
        spuService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    private SaveSpuCommand toCommand(SaveSpuRequest request) {
        return new SaveSpuCommand(
                request.categoryId(),
                null,
                request.spuName(),
                request.subtitle(),
                request.description(),
                request.detailHtml(),
                request.mainImage(),
                request.images(),
                request.tags(),
                request.packagingFee(),
                request.minPurchaseQty(),
                request.limitPerOrder(),
                request.status(),
                request.sortWeight()
        );
    }
}
