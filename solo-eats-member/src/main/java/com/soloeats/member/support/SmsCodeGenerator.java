package com.soloeats.member.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 验证码生成：{@code app.auth.mock-sms-enabled=true}（默认，仅 local/dev）时固定 000000 且不真发短信；
 * 关闭后生成 6 位 SecureRandom 数字。
 */
@Component
public class SmsCodeGenerator {

    private static final String MOCK_CODE = "000000";
    private static final int BOUND = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean mockEnabled;

    public SmsCodeGenerator(@Value("${app.auth.mock-sms-enabled:true}") boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String generate() {
        if (mockEnabled) {
            return MOCK_CODE;
        }
        return String.format("%06d", RANDOM.nextInt(BOUND));
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }
}
