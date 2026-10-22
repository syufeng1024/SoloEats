package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色新增/修改共用请求。修改时字段为 null 表示不改。
 */
public record SaveRoleRequest(
        @Size(max = 64, message = "roleCode 长度不能超过 64")
        String roleCode,

        @Size(max = 64, message = "roleName 长度不能超过 64")
        String roleName,

        @Min(value = 1, message = "roleType 只能是 1平台 2商家 3骑手")
        @Max(value = 3, message = "roleType 只能是 1平台 2商家 3骑手")
        Integer roleType,

        @Min(value = 1, message = "dataScope 只能是 1~5")
        @Max(value = 5, message = "dataScope 只能是 1~5")
        Integer dataScope,

        Integer sort,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status,

        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark
) {

    /** PUT /admin/roles/{id}/menus 的请求体，menuIds 全量替换；空数组表示清空授权。 */
    public record RoleMenusRequest(
            @NotNull(message = "menuIds 不能为空，清空授权请传空数组")
            List<Long> menuIds
    ) {
    }
}
