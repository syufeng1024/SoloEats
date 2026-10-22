package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 岗位新增/修改请求。修改时 postCode 不参与变更。
 */
public record SavePostRequest(
        @NotBlank(message = "postCode 不能为空")
        @Size(max = 64, message = "postCode 长度不能超过 64")
        String postCode,

        @NotBlank(message = "postName 不能为空")
        @Size(max = 64, message = "postName 长度不能超过 64")
        String postName,

        Integer sort,

        @Min(value = 0, message = "status 只能是 0启用 1停用")
        @Max(value = 1, message = "status 只能是 0启用 1停用")
        Integer status,

        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark
) {
}
