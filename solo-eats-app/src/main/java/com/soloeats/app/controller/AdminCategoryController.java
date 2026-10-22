package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveCategoryRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.CategoryService;
import com.soloeats.product.service.command.SaveCategoryCommand;
import com.soloeats.product.service.view.CategoryNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台品类树：最多两级，删除前校验无子级、无 SPU 引用。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @RequirePermission("product:category:list")
    @GetMapping("/tree")
    public ApiResponse<List<CategoryNode>> tree() {
        return ApiResponse.ok(categoryService.tree());
    }

    @RequirePermission("product:category:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "商品管理", action = "新增品类")
    @PostMapping
    public ApiResponse<CategoryNode> create(@Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.ok(categoryService.create(toCommand(request)));
    }

    @RequirePermission("product:category:update")
    @OperationLog(module = "商品管理", action = "编辑品类")
    @PutMapping("/{id}")
    public ApiResponse<CategoryNode> update(@PathVariable Long id,
                                            @Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, toCommand(request)));
    }

    @RequirePermission("product:category:delete")
    @OperationLog(module = "商品管理", action = "删除品类")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }

    private SaveCategoryCommand toCommand(SaveCategoryRequest request) {
        return new SaveCategoryCommand(
                request.parentId(),
                request.categoryName(),
                request.iconUrl(),
                request.sort(),
                request.status()
        );
    }
}
