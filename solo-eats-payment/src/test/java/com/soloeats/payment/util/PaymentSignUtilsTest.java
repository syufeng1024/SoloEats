package com.soloeats.payment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSignUtilsTest {

    private static final String SECRET = "test-callback-secret";
    private static final String PAYMENT_NO = "PAY20261021120000ABCD1234";
    private static final String TRADE_NO = "WX202610210001";
    private static final long AMOUNT = 3800L;

    @Test
    @DisplayName("签名固定为 64 位小写十六进制且可复现")
    void signIsStableHex() {
        String first = PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT);
        String second = PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT);

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("正确签名通过校验")
    void verifyAcceptsCorrectSign() {
        String sign = PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT);

        assertThat(PaymentSignUtils.verify(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT, sign)).isTrue();
    }

    @Test
    @DisplayName("金额、流水号、密钥任一被篡改则校验失败")
    void verifyRejectsTamperedContent() {
        String sign = PaymentSignUtils.sign(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT);

        assertThat(PaymentSignUtils.verify(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT + 1, sign)).isFalse();
        assertThat(PaymentSignUtils.verify(SECRET, PAYMENT_NO, "WX-OTHER", AMOUNT, sign)).isFalse();
        assertThat(PaymentSignUtils.verify("other-secret", PAYMENT_NO, TRADE_NO, AMOUNT, sign)).isFalse();
    }

    @Test
    @DisplayName("签名为空或长度不符时直接失败")
    void verifyRejectsMissingSign() {
        assertThat(PaymentSignUtils.verify(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT, null)).isFalse();
        assertThat(PaymentSignUtils.verify(SECRET, PAYMENT_NO, TRADE_NO, AMOUNT, "abcd")).isFalse();
    }
}
