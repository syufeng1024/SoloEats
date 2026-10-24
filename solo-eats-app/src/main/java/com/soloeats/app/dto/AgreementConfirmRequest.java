package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 隐私协议确认。agree 为空按同意处理，false 表示撤回同意（同样写入历史记录）。
 */
public record AgreementConfirmRequest(
        @NotBlank(message = "协议版本不能为空")
        @Size(max = 32, message = "协议版本最长 32 个字符")
        String agreementVersion,
        Boolean agree
) {
}
