package com.soloeats.service.domain.enums;

/**
 * 工单状态。
 *
 * <p>合法流转：0待受理 → 1处理中 → 2待回访 → 3已办结；0/1/2 → 4已关闭。
 * 禁止跳级（0→3）、禁止逆向回退；3 与 4 为终态。
 */
public enum TicketStatus {

    PENDING(0, "待受理"),
    PROCESSING(1, "处理中"),
    WAIT_CALLBACK(2, "待回访"),
    FINISHED(3, "已办结"),
    CLOSED(4, "已关闭");

    private final int code;
    private final String label;

    TicketStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TicketStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TicketStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }

    /**
     * 状态流转表。null / 未知状态 / 同状态 / 终态出边一律返回 false。
     */
    public static boolean canTransit(Integer from, Integer to) {
        TicketStatus source = fromCode(from);
        TicketStatus target = fromCode(to);
        if (source == null || target == null) {
            return false;
        }
        return switch (source) {
            case PENDING -> target == PROCESSING || target == CLOSED;
            case PROCESSING -> target == WAIT_CALLBACK || target == CLOSED;
            case WAIT_CALLBACK -> target == FINISHED || target == CLOSED;
            case FINISHED, CLOSED -> false;
        };
    }
}
