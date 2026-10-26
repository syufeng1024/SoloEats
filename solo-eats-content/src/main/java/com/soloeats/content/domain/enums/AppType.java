package com.soloeats.content.domain.enums;

/** App 端类型。 */
public enum AppType {

    USER(1, "用户端"),
    RIDER(2, "骑手端"),
    MERCHANT(3, "商家端");

    private final int code;
    private final String label;

    AppType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AppType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AppType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
