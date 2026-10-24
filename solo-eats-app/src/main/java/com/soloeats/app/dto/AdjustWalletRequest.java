package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 后台手工调账。amount 可负，单位分；requestId 参与 uk_biz 幂等。
 */
public record AdjustWalletRequest(
        @NotNull(message = "调账金额不能为空")
        Long amount,
        @NotBlank(message = "调账原因不能为空")
        @Size(max = 200, message = "调账原因最长 200 个字符")
        String reason,
        @NotBlank(message = "requestId 不能为空")
        @Size(max = 64, message = "requestId 最长 64 个字符")
        String requestId
) {
}
