package com.soloeats.message.domain.enums;

import com.soloeats.common.auth.Roles;

import java.util.Set;

/**
 * 消息接收者类型，与 t_site_message.receiver_type / t_notify_setting.receiver_type 一致。
 * 登录态没有显式 receiverType 字段，只能从 JWT 里的 roles 推断。
 */
public enum ReceiverType {

    USER(1),
    MERCHANT(2),
    RIDER(3),
    PLATFORM(4);

    private final int code;

    ReceiverType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 角色 → 接收者类型；无法判定时返回 null，调用方负责拒绝请求。 */
    public static Integer resolve(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        if (roles.contains(Roles.C_USER)) {
            return USER.code;
        }
        if (roles.contains(Roles.MERCHANT)) {
            return MERCHANT.code;
        }
        if (roles.contains(Roles.RIDER)) {
            return RIDER.code;
        }
        boolean platform = roles.stream()
                .anyMatch(role -> role != null && role.startsWith(Roles.ADMIN));
        return platform ? PLATFORM.code : null;
    }

    public static boolean isValid(Integer code) {
        return code != null && code >= USER.code && code <= PLATFORM.code;
    }
}
