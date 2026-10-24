package com.soloeats.app.security;

import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.enums.ReceiverType;

/**
 * 站内信接收者取自登录态，不接受前端传 userId/receiverId。
 * 角色无法映射到接收者类型时直接 10004，避免把消息发到错误主体。
 */
public final class CurrentReceiver {

    private CurrentReceiver() {
    }

    public static UserPrincipal principal() {
        return AuthContext.require();
    }

    public static Integer receiverType(UserPrincipal principal) {
        Integer receiverType = ReceiverType.resolve(principal.roles());
        if (receiverType == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无法识别当前身份的消息接收类型");
        }
        return receiverType;
    }
}
