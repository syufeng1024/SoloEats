package com.soloeats.system.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 账号密码哈希。只允许 bcrypt（强度 10），禁止 MD5/SHA 裸哈希。
 */
@Component
public class PasswordHasher {

    private static final int BCRYPT_STRENGTH = 10;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    public String encode(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException ex) {
            // 库里存了非 bcrypt 值时不泄露细节，按认证失败处理
            return false;
        }
    }
}
