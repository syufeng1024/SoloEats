package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveUserRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.system.service.SysUserService;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.command.UpdateUserCommand;
import com.soloeats.system.service.view.SysUserView;
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

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserService sysUserService;

    @GetMapping
    public ApiResponse<PageResult<SysUserView>> list(
            @RequestParam(required = false) Integer userType,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysUserService.page(
                userType, merchantId, shopId, status, keyword, pageNum, pageSize));
    }

    /**
     * 防重窗口 60s，key 走注解默认的「方法签名 + 参数哈希」：
     * Idempotent 只有 key/ttlSeconds 两个属性，写死 key 会把所有管理员的新增请求一起挡掉。
     */
    @Idempotent(ttlSeconds = 60)
    @PostMapping
    public ApiResponse<SysUserView> create(@Valid @RequestBody SaveUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
                request.username(),
                request.password(),
                request.nickname(),
                request.phone(),
                request.userType(),
                request.merchantId(),
                request.shopId(),
                request.riderId(),
                request.deptId(),
                request.dataScope(),
                request.roleIds()
        );
        return ApiResponse.ok(sysUserService.create(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysUserView> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveUserRequest request) {
        UpdateUserCommand command = new UpdateUserCommand(
                request.nickname(),
                request.phone(),
                request.userType(),
                request.merchantId(),
                request.shopId(),
                request.riderId(),
                request.deptId(),
                request.dataScope(),
                request.roleIds()
        );
        return ApiResponse.ok(sysUserService.update(id, command));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SaveUserRequest.UserStatusRequest request) {
        sysUserService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody SaveUserRequest.UserRolesRequest request) {
        sysUserService.assignRoles(id, request.roleIds());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody SaveUserRequest.ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request.newPassword());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ApiResponse.ok();
    }
}
