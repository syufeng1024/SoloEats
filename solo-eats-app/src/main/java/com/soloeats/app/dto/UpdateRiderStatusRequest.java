package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRiderStatusRequest(
        @NotNull(message = "status 不能为空")
        @Min(value = 0, message = "status 只能是 0 正常 / 1 停用 / 2 拉黑")
        @Max(value = 2, message = "status 只能是 0 正常 / 1 停用 / 2 拉黑")
        Integer status
) {
}
