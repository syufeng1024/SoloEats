package com.soloeats.app.security;

import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.message.domain.enums.ReceiverType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信接收者取自登录态，不接受前端传 userId/receiverId。
 * 角色无法映射到接收者类型时直接 10004，避免把消息发到错误主体。
 *
 * <p>receiverType 是 1用户/2商家/3骑手/4平台；receiverId 一律是业务主体 ID：
 * 用户与平台取登录账号 ID，商家取 merchant_id，骑手取 t_rider.id。
 * 这条口径必须与 t_site_message.receiver_id、t_im_session.peer_id、
 * WebSocketSessionRegistry 的注册 key 保持一致，否则推送会落到空会话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentReceiver {

    private static final String UNRESOLVED_SUBJECT = "无法确定当前身份的业务主体 ID";

    private final RiderService riderService;

    public UserPrincipal principal() {
        return AuthContext.require();
    }

    public Integer receiverType(UserPrincipal principal) {
        Integer receiverType = ReceiverType.resolve(principal.roles());
        if (receiverType == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无法识别当前身份的消息接收类型");
        }
        return receiverType;
    }

    /**
     * 按接收者类型解析业务主体 ID；解析不到直接拒绝，不回落到 userId，
     * 否则商家/骑手会被注册到与推送目标不一致的 key 上。
     */
    public Long receiverId(UserPrincipal principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, UNRESOLVED_SUBJECT);
        }
        Integer receiverType = receiverType(principal);
        if (receiverType == ReceiverType.MERCHANT.getCode()) {
            return requirePositive(principal.merchantId());
        }
        if (receiverType == ReceiverType.RIDER.getCode()) {
            return requireRiderId(principal.userId());
        }
        // C_USER 的 userId 即 t_member_user.id；平台后台取 t_sys_user.id
        return requirePositive(principal.userId());
    }

    private Long requirePositive(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, UNRESOLVED_SUBJECT);
        }
        return id;
    }

    /** 骑手 token 里的 userId 是 t_sys_user.id，需反查 t_rider.id 才能对上 peer_id。 */
    private Long requireRiderId(Long userId) {
        try {
            return requirePositive(riderService.requireRiderIdByUserId(userId));
        } catch (BizException ex) {
            log.debug("resolve rider id failed userId={} reason={}", userId, ex.getMessage());
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, UNRESOLVED_SUBJECT);
        }
    }
}
