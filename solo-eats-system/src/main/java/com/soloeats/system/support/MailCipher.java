package com.soloeats.system.support;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SMTP 密码的 AES-GCM 存取。密钥来自 {@code app.crypto.mail-key}（环境变量 MAIL_KEY），
 * 通过 SHA-256 派生为 32 字节 AES 密钥；密文格式为 Base64(iv || ciphertext || tag)。
 *
 * <p>密钥未配置时拒绝加解密，避免以空密钥写入"看似加密"的数据。
 */
@Component
public class MailCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String configuredKey;

    public MailCipher(@Value("${app.crypto.mail-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(configuredKey);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "password 不能为空");
        }
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // 不输出明文与密文细节
            throw new BizException(ErrorCode.SYSTEM_ERROR, "邮件密码加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "邮件账号密码未配置");
        }
        requireConfigured();
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalStateException("cipher text too short");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "邮件密码解密失败，请检查 MAIL_KEY 是否变更");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "邮件密钥未配置（环境变量 MAIL_KEY），拒绝读写 SMTP 密码");
        }
    }

    private SecretKeySpec deriveKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
