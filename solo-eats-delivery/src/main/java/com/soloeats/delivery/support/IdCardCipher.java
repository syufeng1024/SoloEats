package com.soloeats.delivery.support;

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
import java.util.regex.Pattern;

/**
 * 身份证处理唯一入口：全角归一、AES-GCM 加密、展示脱敏。
 *
 * <ul>
 *   <li>{@code id_card_cipher}：Base64(iv || ciphertext || tag)，随机 IV，可解密；</li>
 *   <li>{@code id_card_mask}：{@code 110101********1234} 形式的展示值。</li>
 * </ul>
 *
 * <p>密钥来自 {@code app.security.id-card-aes-key}，默认值仅供 local/dev；
 * 任何日志不得输出身份证明文或密文。
 */
@Component
public class IdCardCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dX]$");
    private static final char FULL_WIDTH_ZERO = '０';
    private static final char FULL_WIDTH_NINE = '９';
    private static final char FULL_WIDTH_UPPER_X = 'Ｘ';

    private final String aesKey;

    public IdCardCipher(@Value("${app.security.id-card-aes-key:change_me_id_card_aes_key_32b}") String aesKey) {
        this.aesKey = aesKey;
    }

    /** 去首尾空格与全部空白、全角数字转半角、全角 X 转半角并大写。 */
    public String normalize(String idCard) {
        if (idCard == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(idCard.length());
        for (int i = 0; i < idCard.length(); i++) {
            char ch = idCard.charAt(i);
            if (Character.isWhitespace(ch) || ch == '　') {
                continue;
            }
            if (ch >= FULL_WIDTH_ZERO && ch <= FULL_WIDTH_NINE) {
                ch = (char) ('0' + (ch - FULL_WIDTH_ZERO));
            } else if (ch == FULL_WIDTH_UPPER_X || ch == 'x') {
                ch = 'X';
            }
            builder.append(ch);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    /** 校验已归一化的 18 位身份证；不合法抛 10001。 */
    public void requireValid(String normalizedIdCard) {
        if (normalizedIdCard == null || !ID_CARD_PATTERN.matcher(normalizedIdCard).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "身份证号格式不正确");
        }
    }

    public String encrypt(String idCard) {
        String normalized = normalize(idCard);
        requireValid(normalized);
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // 不输出身份证与密文细节
            throw new BizException(ErrorCode.SYSTEM_ERROR, "身份证加密失败");
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "身份证密文为空");
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
            throw new BizException(ErrorCode.SYSTEM_ERROR, "身份证解密失败，请检查 ID_CARD_AES_KEY 是否变更");
        }
    }

    /** 18 位身份证展示为前 6 + 8 个 * + 后 4；无法识别时返回 ****。 */
    public String mask(String idCard) {
        String normalized = normalize(idCard);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() < 10) {
            return "****";
        }
        int keep = 4;
        return normalized.substring(0, 6) + "********"
                + normalized.substring(normalized.length() - keep);
    }

    private SecretKeySpec deriveKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(aesKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
