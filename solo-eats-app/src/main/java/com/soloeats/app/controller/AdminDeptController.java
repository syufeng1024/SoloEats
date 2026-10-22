package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveDeptRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.system.service.SysOrgService;
import com.soloeats.system.service.command.SaveDeptCommand;
import com.soloeats.system.service.view.DeptNode;
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
@RequestMapping("/api/v1/admin/depts")
@RequiredArgsConstructor
public class AdminDeptController {

    private final SysOrgService sysOrgService;

    @RequirePermission("system:dept:list")
    @GetMapping("/tree")
    public ApiResponse<List<DeptNode>> tree(@RequestParam(required = false) Integer status) {
        return ApiResponse.ok(sysOrgService.deptTree(status));
    }

    @RequirePermission("system:dept:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping
    public ApiResponse<DeptNode> create(@Valid @RequestBody SaveDeptRequest request) {
        return ApiResponse.ok(sysOrgService.createDept(toCommand(request)));
    }

    @RequirePermission("system:dept:update")
    @PutMapping("/{id}")
    public ApiResponse<DeptNode> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveDeptRequest request) {
        return ApiResponse.ok(sysOrgService.updateDept(id, toCommand(request)));
    }

    @RequirePermission("system:dept:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysOrgService.deleteDept(id);
        return ApiResponse.ok();
    }

    private SaveDeptCommand toCommand(SaveDeptRequest request) {
        return new SaveDeptCommand(
                request.parentId(),
                request.deptName(),
                request.leader(),
                request.phone(),
                request.sort(),
                request.status());
    }
}
