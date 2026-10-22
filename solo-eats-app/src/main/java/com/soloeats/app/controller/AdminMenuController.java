package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveMenuRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.system.service.SysMenuService;
import com.soloeats.system.service.command.SaveMenuCommand;
import com.soloeats.system.service.view.MenuNode;
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

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final SysMenuService sysMenuService;

    @RequirePermission("system:menu:list")
    @GetMapping("/tree")
    public ApiResponse<List<MenuNode>> tree(@RequestParam(required = false) Integer menuType) {
        return ApiResponse.ok(sysMenuService.tree(menuType));
    }

    @RequirePermission("system:menu:create")
    @PostMapping
    public ApiResponse<MenuNode> create(@Valid @RequestBody SaveMenuRequest request) {
        return ApiResponse.ok(sysMenuService.create(toCommand(request)));
    }

    @RequirePermission("system:menu:update")
    @PutMapping("/{id}")
    public ApiResponse<MenuNode> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveMenuRequest request) {
        return ApiResponse.ok(sysMenuService.update(id, toCommand(request)));
    }

    @RequirePermission("system:menu:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ApiResponse.ok();
    }

    private SaveMenuCommand toCommand(SaveMenuRequest request) {
        return new SaveMenuCommand(
                request.parentId(),
                request.menuName(),
                request.menuType(),
                request.path(),
                request.component(),
                request.perms(),
                request.icon(),
                request.sort(),
                request.visible(),
                request.status()
        );
    }
}
