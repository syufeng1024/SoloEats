package com.soloeats.delivery.domain.enums;

/**
 * t_rider.cert_status。与 t_rider_certification.status（0待审 1通过 2驳回）不是同一套取值。
 */
public enum RiderCertStatus {

    NOT_SUBMITTED(0),
    PENDING(1),
    APPROVED(2),
    REJECTED(3);

    private final int code;

    RiderCertStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isApproved(Integer code) {
        return code != null && code == APPROVED.code;
    }
}
