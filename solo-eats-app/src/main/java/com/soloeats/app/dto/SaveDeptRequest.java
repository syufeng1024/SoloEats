package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 部门新增/修改请求。修改时字段为 null 表示保持不变；parentId=0 表示根部门。
 */
public record SaveDeptRequest(
        Long parentId,

        @NotBlank(message = "deptName 不能为空")
        @Size(max = 64, message = "deptName 长度不能超过 64")
        String deptName,

        @Size(max = 64, message = "leader 长度不能超过 64")
        String leader,

        @Size(max = 32, message = "phone 长度不能超过 32")
        String phone,

        Integer sort,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status
) {
}
