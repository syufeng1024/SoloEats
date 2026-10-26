package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintJudgeRequest(
        @NotNull(message = "liability 不能为空")
        @Min(value = 1, message = "liability 只能是 1商家 2骑手 3平台 4用户")
        @Max(value = 4, message = "liability 只能是 1商家 2骑手 3平台 4用户")
        Integer liability,
        @NotBlank(message = "result 不能为空")
        @Size(max = 500, message = "result 最长 500 个字符")
        String result,
        @NotNull(message = "punishAction 不能为空")
        @Min(value = 0, message = "punishAction 只能是 0无 1警告 2扣款 3封禁")
        @Max(value = 3, message = "punishAction 只能是 0无 1警告 2扣款 3封禁")
        Integer punishAction
) {
}
