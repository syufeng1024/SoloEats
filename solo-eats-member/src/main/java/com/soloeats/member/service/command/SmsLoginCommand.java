package com.soloeats.member.service.command;

/**
 * 手机号 + 验证码登录入参。验证码一次性使用，登录成功后立即失效。
 */
public record SmsLoginCommand(String phone, String code) {
}
