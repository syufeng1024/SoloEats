package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditShopSpuRequest(
        @NotNull(message = "auditStatus 不能为空")
        Integer auditStatus,
        @Size(max = 255, message = "remark 长度不能超过 255")
        String remark
) {
}
