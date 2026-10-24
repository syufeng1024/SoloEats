package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 模拟充值，金额单位分。上下限（1 ~ 100000）由 MemberWalletService 统一校验并返回 80007，
 * 不在 DTO 层用 @Positive 提前转成 10001。
 */
public record RechargeRequest(
        @NotNull(message = "充值金额不能为空")
        Long amount
) {
}
