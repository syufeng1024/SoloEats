package com.soloeats.content.domain.enums;

/** 搜索词类型。屏蔽词只参与服务端过滤，不返回前端。 */
public enum KeywordType {

    DEFAULT(1, "默认词"),
    HOT(2, "热搜词"),
    SUGGEST(3, "联想词"),
    BLOCKED(4, "屏蔽词");

    private final int code;
    private final String label;

    KeywordType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static KeywordType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (KeywordType value : values()) {
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
