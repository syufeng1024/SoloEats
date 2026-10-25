package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提现申请。amount 单位为分；subjectType/subjectId 一律从登录态解析，不接受请求体传入。
 */
public record PayoutApplyRequest(
        @NotNull(message = "amount 不能为空")
        @Min(value = 1, message = "amount 必须大于 0")
        Long amount
) {
}
