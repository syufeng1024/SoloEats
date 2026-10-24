package com.soloeats.member.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

/**
 * 短信验证码使用场景：1 登录 / 注册、2 商家入驻、3 手机号改绑。
 */
public enum SmsScene {

    LOGIN(1),
    MERCHANT_APPLY(2),
    PHONE_CHANGE(3);

    private final int code;

    SmsScene(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static SmsScene fromCode(Integer code) {
        if (code != null) {
            for (SmsScene scene : values()) {
                if (scene.code == code) {
                    return scene;
                }
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知验证码场景: " + code);
    }
}
