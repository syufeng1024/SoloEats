package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 后台会员状态变更：0 正常 / 1 禁用 / 2 拉黑。
 */
public record UpdateMemberStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能是 0 / 1 / 2")
        @Max(value = 2, message = "状态只能是 0 / 1 / 2")
        Integer status
) {
}
