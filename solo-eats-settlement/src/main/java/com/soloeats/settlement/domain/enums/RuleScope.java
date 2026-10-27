package com.soloeats.settlement.domain.enums;

/**
 * 抽佣规则作用域，与 t_commission_rule.rule_scope 对齐：
 * 1 全局默认、2 品类、3 商家、4 活动。
 *
 * <p>命中优先级由 {@link #HIT_PRIORITY} 固定为「活动级 > 商家级 > 品类级 > 全局默认」，
 * 顺序即业务口径，任何实现都不得调整。
 */
public enum RuleScope {

    GLOBAL(1, "全局默认"),
    CATEGORY(2, "品类级"),
    MERCHANT(3, "商家级"),
    PROMOTION(4, "活动级");

    private final int code;
    private final String description;

    RuleScope(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 命中优先级（高 → 低）：活动 > 商家 > 品类 > 全局。
     */
    public static RuleScope[] hitPriority() {
        return new RuleScope[]{PROMOTION, MERCHANT, CATEGORY, GLOBAL};
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (RuleScope scope : values()) {
            if (scope.code == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 code 取枚举，非法值抛 {@link IllegalArgumentException}，由调用方转成参数错误。
     */
    public static RuleScope of(Integer code) {
        if (code != null) {
            for (RuleScope scope : values()) {
                if (scope.code == code) {
                    return scope;
                }
            }
        }
        throw new IllegalArgumentException("ruleScope 只能是 1全局 2品类 3商家 4活动");
    }

    /**
     * 是否需要 ref_id（品类/商家/活动必须带，全局必须为空）。
     */
    public boolean requireRefId() {
        return this != GLOBAL;
    }
}
