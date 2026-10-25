package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提现单审核请求。pass=true 通过，false 驳回并写 remark 到 audit_remark。
 */
public record PayoutAuditRequest(
        @NotNull(message = "pass 不能为空")
        Boolean pass,

        @Size(max = 255, message = "remark 不能超过 255 个字符")
        String remark
) {
}
