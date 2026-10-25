package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 结算单审核请求。pass=true 通过，false 驳回。
 */
public record BillAuditRequest(
        @NotNull(message = "pass 不能为空")
        Boolean pass,

        @Size(max = 255, message = "remark 不能超过 255 个字符")
        String remark
) {
}
