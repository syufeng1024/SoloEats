package com.soloeats.marketing.support;

/**
 * 邀请码编解码：userId XOR 固定盐后做 Base62。
 *
 * <p>不落库，同一 userId 永远得到同一码。盐不是安全密钥，只做混淆；
 * 真正的防刷在 {@code InviteServiceImpl} 的绑定次数限制。
 */
public final class InviteCodec {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    /** 最高位为 0，保证与正数 userId 异或后仍为正数 */
    private static final long SALT = 0x3F1A2B4C5D6E7F80L;
    private static final int MAX_CODE_LENGTH = 16;

    private InviteCodec() {
    }

    public static String encode(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return toBase62(userId ^ SALT);
    }

    public static long decode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("invite code is null");
        }
        String normalized = code.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("invalid invite code length");
        }
        try {
            long userId = fromBase62(normalized) ^ SALT;
            if (userId <= 0 || !encode(userId).equals(normalized)) {
                throw new IllegalArgumentException("invalid invite code");
            }
            return userId;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("invalid invite code", e);
        }
    }

    private static String toBase62(long value) {
        if (value == 0) {
            return "0";
        }
        StringBuilder builder = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            builder.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return builder.reverse().toString();
    }

    private static long fromBase62(String code) {
        long value = 0L;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid invite code character");
            }
            value = Math.addExact(Math.multiplyExact(value, BASE), digit);
        }
        return value;
    }
}
