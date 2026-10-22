package com.soloeats.merchant.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

import java.util.Set;

/**
 * 资质类型。提交审核前必须齐备营业执照、食品经营许可证、法人身份证正反面。
 */
public enum QualType {

    BUSINESS_LICENSE(1),
    FOOD_PERMIT(2),
    ID_CARD_FRONT(3),
    ID_CARD_BACK(4),
    STORE_FRONT(5),
    STORE_INNER(6),
    OTHER(7);

    /** 提交审核的必传资质。 */
    public static final Set<Integer> REQUIRED = Set.of(
            BUSINESS_LICENSE.code, FOOD_PERMIT.code, ID_CARD_FRONT.code, ID_CARD_BACK.code);

    private final int code;

    QualType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static QualType fromCode(Integer code) {
        if (code != null) {
            for (QualType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知资质类型: " + code);
    }
}
