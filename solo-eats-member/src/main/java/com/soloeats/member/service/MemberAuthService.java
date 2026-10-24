package com.soloeats.member.service;

import com.soloeats.member.domain.enums.SmsScene;
import com.soloeats.member.service.command.SmsLoginCommand;
import com.soloeats.member.service.view.SmsLoginResult;

/**
 * C 端账号认证：短信验证码下发与手机号登录（登录即注册）。
 */
public interface MemberAuthService {

    /**
     * 下发验证码。按手机号哈希维度限流（1 次/分钟、5 次/小时），超限抛 10005；
     * 验证码落库 5 分钟有效，明文手机号不入库、不入日志。
     */
    void sendSmsCode(String phone, SmsScene scene);

    /**
     * 手机号 + 验证码登录。用户不存在时在同一事务内创建用户与钱包；
     * 用户 status != 0 抛 80002，验证码不可用抛 80003/80004。
     */
    SmsLoginResult loginBySms(SmsLoginCommand command);
}
