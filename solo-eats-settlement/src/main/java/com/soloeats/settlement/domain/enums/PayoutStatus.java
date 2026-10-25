package com.soloeats.settlement.domain.enums;

/**
 * 提现单状态。与 t_payout_order.status 对齐：
 * 0 待审核 → 10 审核通过 → 20 打款中 → 30 已到账；0 → 40 已驳回。
 * 状态机不允许跳级与逆向，落库时一律用条件更新 WHERE id=? AND status=? 兜底。
 */
public enum PayoutStatus {

    PENDING(0, "待审核"),
    APPROVED(10, "审核通过"),
    PAYING(20, "打款中"),
    ARRIVED(30, "已到账"),
    REJECTED(40, "已驳回");

    private final int code;
    private final String description;

    PayoutStatus(int code, String description) {
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
     * 合法流转表：0→10/40，10→20，20→30；其余（含同级、逆向、跳级）全部非法。
     */
    public static boolean canTransit(int from, int to) {
        return switch (from) {
            case 0 -> to == APPROVED.code || to == REJECTED.code;
            case 10 -> to == PAYING.code;
            case 20 -> to == ARRIVED.code;
            default -> false;
        };
    }
}
