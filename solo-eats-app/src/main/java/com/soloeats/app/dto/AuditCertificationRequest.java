package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditCertificationRequest(
        @NotNull(message = "审核结论不能为空")
        @Min(value = 1, message = "审核结论只能是 1 通过 / 2 驳回")
        @Max(value = 2, message = "审核结论只能是 1 通过 / 2 驳回")
        Integer status,

        @Size(max = 255, message = "审核备注不能超过 255 个字符")
        String remark
) {
}
