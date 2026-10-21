package com.soloeats.system.service;

import com.soloeats.system.service.command.PasswordLoginCommand;
import com.soloeats.system.service.view.LoginResult;

public interface AuthService {

    /**
     * 用户名 + 密码登录：bcrypt 校验通过后签发 JWT。
     * 只读操作，不加事务。
     */
    LoginResult login(PasswordLoginCommand command);
}
