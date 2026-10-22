package com.soloeats.merchant.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

/**
 * 入驻状态机：0草稿 → 1待审核 → 2已通过 | 3已驳回；3已驳回可重提为 1待审核；
 * 2已通过 ↔ 4已停用。禁止 0 → 2 跳过审核。
 */
public enum MerchantStatus {

    DRAFT(0),
    PENDING(1),
    APPROVED(2),
    REJECTED(3),
    DISABLED(4);

    private final int code;

    MerchantStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MerchantStatus fromCode(Integer code) {
        if (code != null) {
            for (MerchantStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BizException(ErrorCode.MERCHANT_STATUS_INVALID, "未知商家状态: " + code);
    }

    /** 草稿或已驳回可提交审核。 */
    public static boolean canSubmit(Integer status) {
        return status != null && (status == DRAFT.code || status == REJECTED.code);
    }

    /** 只有待审核可以审核通过或驳回。 */
    public static boolean canAudit(Integer status) {
        return status != null && status == PENDING.code;
    }

    /** 启用/停用只允许在 2 与 4 之间切换。 */
    public static boolean canToggle(Integer current, Integer target) {
        if (current == null || target == null) {
            return false;
        }
        return (current == APPROVED.code && target == DISABLED.code)
                || (current == DISABLED.code && target == APPROVED.code);
    }
}
