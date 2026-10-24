package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台标签定义。autoFlag 0 手动 1 自动；status 0 启用 1 停用。
 */
public record SaveTagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 64, message = "标签名称最长 64 个字符")
        String name,
        @Size(max = 255, message = "分群规则最长 255 个字符")
        String ruleDesc,
        @NotNull(message = "autoFlag 不能为空")
        @Min(value = 0, message = "autoFlag 只能是 0 或 1")
        @Max(value = 1, message = "autoFlag 只能是 0 或 1")
        Integer autoFlag,
        @NotNull(message = "status 不能为空")
        @Min(value = 0, message = "status 只能是 0 或 1")
        @Max(value = 1, message = "status 只能是 0 或 1")
        Integer status
) {
}
