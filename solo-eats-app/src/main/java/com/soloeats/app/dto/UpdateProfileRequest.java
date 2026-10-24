package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 更新 C 端个人资料。字段为空表示不修改；手机号必须走 /me/phone 换绑。
 */
public record UpdateProfileRequest(
        @Size(max = 64, message = "昵称最长 64 个字符")
        String nickname,
        @Size(max = 512, message = "头像地址最长 512 个字符")
        String avatarUrl,
        @Min(value = 0, message = "性别取值只能是 0/1/2")
        @Max(value = 2, message = "性别取值只能是 0/1/2")
        Integer gender,
        @PastOrPresent(message = "生日不能晚于今天")
        LocalDate birthday
) {
}
