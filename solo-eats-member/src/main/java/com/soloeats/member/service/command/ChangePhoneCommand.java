package com.soloeats.member.service.command;

/**
 * 手机号换绑。验证码必须是在新手机号上以 scene=3（改绑）下发的那一条。
 */
public record ChangePhoneCommand(
        String newPhone,
        String code
) {
}
