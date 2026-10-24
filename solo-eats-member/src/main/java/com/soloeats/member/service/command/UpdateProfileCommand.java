package com.soloeats.member.service.command;

import java.time.LocalDate;

/**
 * 更新个人资料。字段为 null 表示不修改；手机号不在本命令范围内，
 * 换绑必须走 {@link ChangePhoneCommand} 并校验短信验证码。
 */
public record UpdateProfileCommand(
        String nickname,
        String avatarUrl,
        Integer gender,
        LocalDate birthday
) {
}
