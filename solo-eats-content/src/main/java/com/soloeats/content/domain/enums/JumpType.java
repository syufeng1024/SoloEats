package com.soloeats.content.domain.enums;

/** Banner 跳转类型。5 表示无跳转，此时 jumpValue 必须为空。 */
public enum JumpType {

    PRODUCT(1, "商品"),
    SHOP(2, "店铺"),
    ACTIVITY(3, "活动"),
    PAGE(4, "页面"),
    NONE(5, "无跳转");

    private final int code;
    private final String label;

    JumpType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static JumpType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (JumpType value : values()) {
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
