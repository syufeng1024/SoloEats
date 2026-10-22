package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveRoleRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.system.service.SysRoleService;
import com.soloeats.system.service.command.SaveRoleCommand;
import com.soloeats.system.service.view.SysRoleView;
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

import java.util.List;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final SysRoleService sysRoleService;

    @RequirePermission("system:role:list")
    @GetMapping
    public ApiResponse<PageResult<SysRoleView>> list(
            @RequestParam(required = false) Integer roleType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysRoleService.page(roleType, status, pageNum, pageSize));
    }

    @RequirePermission("system:role:create")
    @PostMapping
    public ApiResponse<SysRoleView> create(@Valid @RequestBody SaveRoleRequest request) {
        return ApiResponse.ok(sysRoleService.create(toCommand(request)));
    }

    @RequirePermission("system:role:update")
    @PutMapping("/{id}")
    public ApiResponse<SysRoleView> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveRoleRequest request) {
        return ApiResponse.ok(sysRoleService.update(id, toCommand(request)));
    }

    @RequirePermission("system:role:list")
    @GetMapping("/{id}/menus")
    public ApiResponse<RoleMenuIds> listMenus(@PathVariable Long id) {
        return ApiResponse.ok(new RoleMenuIds(sysRoleService.listMenuIds(id)));
    }

    @RequirePermission("system:role:assign")
    @PutMapping("/{id}/menus")
    public ApiResponse<Void> assignMenus(
            @PathVariable Long id,
            @Valid @RequestBody SaveRoleRequest.RoleMenusRequest request) {
        sysRoleService.assignMenus(id, request.menuIds());
        return ApiResponse.ok();
    }

    private SaveRoleCommand toCommand(SaveRoleRequest request) {
        return new SaveRoleCommand(
                request.roleCode(),
                request.roleName(),
                request.roleType(),
                request.dataScope(),
                request.sort(),
                request.status(),
                request.remark()
        );
    }

    /** 角色已授权菜单，前端按 menuIds 勾选复选框。 */
    public record RoleMenuIds(List<Long> menuIds) {
    }
}
