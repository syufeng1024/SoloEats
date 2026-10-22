package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 账号新增/修改共用请求。修改接口忽略 username / password；
 * 必填项由 SysUserService 按场景校验（新增必填用户名密码，修改可缺省表示不改）。
 */
public record SaveUserRequest(
        @Size(max = 64, message = "username 长度不能超过 64")
        String username,

        @Size(min = 8, max = 64, message = "password 长度必须是 8~64")
        String password,

        @Size(max = 64, message = "nickname 长度不能超过 64")
        String nickname,

        @Size(max = 32, message = "phone 长度不能超过 32")
        String phone,

        @Min(value = 1, message = "userType 只能是 1平台 2商家 3骑手")
        @Max(value = 3, message = "userType 只能是 1平台 2商家 3骑手")
        Integer userType,

        Long merchantId,

        Long shopId,

        Long riderId,

        Long deptId,

        @Min(value = 1, message = "dataScope 只能是 1~5")
        @Max(value = 5, message = "dataScope 只能是 1~5")
        Integer dataScope,

        List<Long> roleIds
) {

    /** PUT /admin/users/{id}/status 的请求体。 */
    public record UserStatusRequest(
            @NotNull(message = "status 不能为空")
            @Min(value = 0, message = "status 只能是 0启用 1停用")
            @Max(value = 1, message = "status 只能是 0启用 1停用")
            Integer status
    ) {
    }

    /** PUT /admin/users/{id}/roles 的请求体，roleIds 全量替换；空数组表示清空授权。 */
    public record UserRolesRequest(
            @NotNull(message = "roleIds 不能为空，清空授权请传空数组")
            List<Long> roleIds
    ) {
    }

    /** POST /admin/users/{id}/reset-password 的请求体。 */
    public record ResetPasswordRequest(
            @NotBlank(message = "newPassword 不能为空")
            @Size(min = 8, max = 64, message = "newPassword 长度必须是 8~64")
            String newPassword
    ) {
    }
}
