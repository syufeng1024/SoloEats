package com.soloeats.member.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneCipherTest {

    private static final String AES_KEY = "unit-test-phone-aes-key";
    private static final String SALT = "unit-test-phone-salt";
    private static final String PHONE = "13800000002";

    private final PhoneCipher phoneCipher = new PhoneCipher(AES_KEY, SALT);

    @Test
    @DisplayName("AES-GCM：加密后解密回原文，随机 IV 使两次密文不同")
    void encryptThenDecrypt() {
        String first = phoneCipher.encrypt(PHONE);
        String second = phoneCipher.encrypt(PHONE);

        assertThat(first).isNotEqualTo(PHONE);
        assertThat(first).isNotEqualTo(second);
        assertThat(phoneCipher.decrypt(first)).isEqualTo(PHONE);
        assertThat(phoneCipher.decrypt(second)).isEqualTo(PHONE);
    }

    @Test
    @DisplayName("同一手机号 hash 稳定，全角数字归一后同值，且为 64 位小写 hex")
    void hashStableAndNormalized() {
        String hash = phoneCipher.hash(PHONE);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(phoneCipher.hash(PHONE)).isEqualTo(hash);
        assertThat(phoneCipher.hash(" １３８０００００００２ ")).isEqualTo(hash);
        assertThat(phoneCipher.hash("13800000003")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("mask：11 位手机号输出 138****0002，非法输入兜底为 ****")
    void maskFormat() {
        assertThat(phoneCipher.mask(PHONE)).isEqualTo("138****0002");
        assertThat(phoneCipher.mask(null)).isNull();
        assertThat(phoneCipher.mask("123")).isEqualTo("****");
    }

    @Test
    @DisplayName("非法手机号在加密与哈希入口被拒绝，避免脏数据入库")
    void rejectInvalidPhone() {
        assertThatThrownBy(() -> phoneCipher.encrypt("12345"))
                .isInstanceOf(com.soloeats.common.exception.BizException.class);
        assertThatThrownBy(() -> phoneCipher.hash("23800000002"))
                .isInstanceOf(com.soloeats.common.exception.BizException.class);
    }
}
