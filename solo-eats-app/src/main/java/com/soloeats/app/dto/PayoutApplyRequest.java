package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 提现申请。amount 单位为分；subjectType/subjectId 一律从登录态解析，不接受请求体传入。
 * channel：1 支付宝、2 银行卡；为空时由 PayoutServiceImpl 按 1 处理。
 */
public record PayoutApplyRequest(
        @NotNull(message = "amount 不能为空")
        @Min(value = 1, message = "amount 必须大于 0")
        Long amount,

        @Min(value = 1, message = "channel 只能是 1支付宝 或 2银行卡")
        @Max(value = 2, message = "channel 只能是 1支付宝 或 2银行卡")
        Integer channel
) {
}
