package com.soloeats.service.domain.enums;

/**
 * 工单类型，与 t_service_ticket.ticket_type 一致。
 */
public enum TicketType {

    ORDER(1, "订单问题"),
    REFUND(2, "退款"),
    DELIVERY(3, "配送"),
    COMPLAINT(4, "投诉"),
    SUGGESTION(5, "建议");

    private final int code;
    private final String label;

    TicketType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TicketType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TicketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
