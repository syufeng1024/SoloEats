package com.soloeats.payment.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PaymentSignUtils {

    private PaymentSignUtils() {
    }

    public static String sign(String secret, String paymentNo, String tradeNo, Long amount) {
        try {
            String content = paymentNo + ":" + tradeNo + ":" + amount;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("payment sign failed", ex);
        }
    }

    public static boolean verify(String secret, String paymentNo, String tradeNo, Long amount, String actualSign) {
        if (actualSign == null) {
            return false;
        }
        String expected = sign(secret, paymentNo, tradeNo, amount);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actualSign.getBytes(StandardCharsets.UTF_8)
        );
    }
}
