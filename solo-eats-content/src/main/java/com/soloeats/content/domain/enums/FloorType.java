package com.soloeats.content.domain.enums;

/**
 * 首页楼层类型。config_json 的结构按类型区分：
 * 2 金刚区含 categoryId，5 推荐店铺含 shopId，6 猜你喜欢含 spuId。
 */
public enum FloorType {

    BANNER(1, "轮播"),
    KING_KONG(2, "金刚区"),
    SECKILL(3, "秒杀"),
    COUPON(4, "神券"),
    RECOMMEND_SHOP(5, "推荐店铺"),
    GUESS_LIKE(6, "猜你喜欢"),
    NOTICE(7, "公告条");

    private final int code;
    private final String label;

    FloorType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FloorType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (FloorType value : values()) {
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
