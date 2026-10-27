package com.soloeats.settlement.domain.enums;

/**
 * 平台资金流水类型，与 t_fund_flow.flow_type 对齐：
 * 1 佣金收入、2 结算支出、3 补贴支出、4 优惠成本、5 提现支出、6 退款冲抵。
 *
 * <p>direction：1 入、2 出。退款冲抵为负项但方向仍记「入」以避免金额变负，
 * 具体正负由报表口径决定（{@code profit} 只扣减真实支出项）。
 * 枚举常量参数里直接用字面量 1/2，避免静态常量前向引用。
 */
public enum FlowType {

    COMMISSION_INCOME(1, "佣金收入", 1),
    SETTLEMENT_EXPENSE(2, "结算支出", 2),
    SUBSIDY_COST(3, "补贴支出", 2),
    DISCOUNT_COST(4, "优惠成本", 2),
    PAYOUT_EXPENSE(5, "提现支出", 2),
    REFUND_OFFSET(6, "退款冲抵", 1);

    /** 资金流入。 */
    public static final int DIRECTION_IN = 1;
    /** 资金流出。 */
    public static final int DIRECTION_OUT = 2;

    private final int code;
    private final String description;
    private final int defaultDirection;

    FlowType(int code, String description, int defaultDirection) {
        this.code = code;
        this.description = description;
        this.defaultDirection = defaultDirection;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultDirection() {
        return defaultDirection;
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (FlowType type : values()) {
            if (type.code == code) {
                return true;
            }
        }
        return false;
    }

    public static FlowType of(Integer code) {
        if (code != null) {
            for (FlowType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("flowType 只能是 1~6");
    }

    public static boolean isValidDirection(Integer direction) {
        return direction != null && (direction == DIRECTION_IN || direction == DIRECTION_OUT);
    }
}
