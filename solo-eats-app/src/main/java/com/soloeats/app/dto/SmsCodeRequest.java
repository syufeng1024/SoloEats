package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 验证码下发入参：scene 1 登录 2 入驻 3 改绑。
 */
public record SmsCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @NotNull(message = "scene 不能为空")
        @Min(value = 1, message = "scene 取值 1~3")
        @Max(value = 3, message = "scene 取值 1~3")
        Integer scene
) {
}
