package com.soloeats.member.support;

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
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 手机号处理唯一入口：AES-GCM 加密、SHA-256 加盐哈希、展示脱敏。
 *
 * <ul>
 *   <li>{@code phone_cipher}：Base64(iv || ciphertext || tag)，随机 IV，可解密；</li>
 *   <li>{@code phone_hash}：SHA-256(手机号 + 盐) 小写 hex，唯一键与查询用，不可逆；</li>
 *   <li>{@code phone_mask}：{@code 138****8000} 形式的展示值。</li>
 * </ul>
 *
 * <p>密钥与盐来自 {@code app.security.phone-aes-key} / {@code app.security.phone-hash-salt}，
 * 默认值仅供 local/dev；生产必须由环境变量注入。任何日志不得输出手机号明文或密文。
 */
@Component
public class PhoneCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final char FULL_WIDTH_ZERO = '０';
    private static final char FULL_WIDTH_NINE = '９';

    private final String aesKey;
    private final String hashSalt;

    public PhoneCipher(@Value("${app.security.phone-aes-key:change_me_phone_aes_key_32bytes}") String aesKey,
                       @Value("${app.security.phone-hash-salt:change_me_phone_hash_salt}") String hashSalt) {
        this.aesKey = aesKey;
        this.hashSalt = hashSalt;
    }

    /**
     * 外部输入归一：去首尾空格与全部空白字符、全角数字转半角、清洗换行符。
     * 不做格式校验，格式由 {@link #requireValid(String)} 负责。
     */
    public String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(phone.length());
        for (int i = 0; i < phone.length(); i++) {
            char ch = phone.charAt(i);
            if (Character.isWhitespace(ch) || ch == '　') {
                continue;
            }
            if (ch >= FULL_WIDTH_ZERO && ch <= FULL_WIDTH_NINE) {
                ch = (char) ('0' + (ch - FULL_WIDTH_ZERO));
            }
            builder.append(ch);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    /** 校验已归一化的手机号；不合法抛 10001。 */
    public void requireValid(String normalizedPhone) {
        if (normalizedPhone == null || !PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
    }

    public String encrypt(String phone) {
        String normalized = normalize(phone);
        requireValid(normalized);
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // 不输出手机号与密文细节
            throw new BizException(ErrorCode.SYSTEM_ERROR, "手机号加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "手机号密文为空");
        }
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "手机号解密失败，请检查 PHONE_AES_KEY 是否变更");
        }
    }

    /** SHA-256(归一化手机号 + 盐) 小写 hex，固定 64 字符。 */
    public String hash(String phone) {
        String normalized = normalize(phone);
        requireValid(normalized);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((normalized + hashSalt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "手机号哈希失败");
        }
    }

    /** 11 位手机号展示为前 3 + **** + 后 4；无法识别时返回 ****。 */
    public String mask(String phone) {
        String normalized = normalize(phone);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() < 7) {
            return "****";
        }
        return normalized.substring(0, 3) + "****"
                + normalized.substring(normalized.length() - 4);
    }

    private SecretKeySpec deriveKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(aesKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
