package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveDictRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.system.service.SysDictService;
import com.soloeats.system.service.command.SaveDictItemCommand;
import com.soloeats.system.service.command.SaveDictTypeCommand;
import com.soloeats.system.service.view.DictItemView;
import com.soloeats.system.service.view.DictTypeView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典维护。类上不加 @RequireRole：{@code GET /{dictType}/items} 需匿名可读，
 * 其余写接口逐个标注角色与权限码。
 */
@RestController
@RequestMapping("/api/v1/admin/dicts")
@RequiredArgsConstructor
public class AdminDictController {

    private final SysDictService sysDictService;

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:list")
    @GetMapping
    public ApiResponse<PageResult<DictTypeView>> list(
            @RequestParam(required = false) String dictType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysDictService.pageTypes(dictType, status, pageNum, pageSize));
    }

    /**
     * 前端渲染枚举用，免登录。只返回启用项，缓存 dict:{tenantId}:{dictType}。
     */
    @GetMapping("/{dictType}/items")
    public ApiResponse<List<DictItemView>> items(@PathVariable String dictType) {
        return ApiResponse.ok(sysDictService.listItems(dictType));
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping
    public ApiResponse<DictTypeView> createType(@Valid @RequestBody SaveDictRequest request) {
        return ApiResponse.ok(sysDictService.createType(new SaveDictTypeCommand(
                request.dictType(),
                request.dictName(),
                request.status(),
                request.remark())));
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:update")
    @PutMapping("/{id}")
    public ApiResponse<DictTypeView> updateType(
            @PathVariable Long id,
            @Valid @RequestBody SaveDictRequest request) {
        return ApiResponse.ok(sysDictService.updateType(id, new SaveDictTypeCommand(
                request.dictType(),
                request.dictName(),
                request.status(),
                request.remark())));
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        sysDictService.deleteType(id);
        return ApiResponse.ok();
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping("/items")
    public ApiResponse<DictItemView> createItem(
            @Valid @RequestBody SaveDictRequest.DictItemRequest request) {
        return ApiResponse.ok(sysDictService.createItem(new SaveDictItemCommand(
                request.dictType(),
                request.dictLabel(),
                request.dictValue(),
                request.dictSort(),
                request.cssClass(),
                request.isDefault(),
                request.status(),
                request.remark())));
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:update")
    @PutMapping("/items/{id}")
    public ApiResponse<DictItemView> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody SaveDictRequest.DictItemRequest request) {
        return ApiResponse.ok(sysDictService.updateItem(id, new SaveDictItemCommand(
                request.dictType(),
                request.dictLabel(),
                request.dictValue(),
                request.dictSort(),
                request.cssClass(),
                request.isDefault(),
                request.status(),
                request.remark())));
    }

    @RequireRole(Roles.ADMIN)
    @RequirePermission("system:dict:delete")
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        sysDictService.deleteItem(id);
        return ApiResponse.ok();
    }
}
