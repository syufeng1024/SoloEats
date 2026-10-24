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

    /**
     * 角色 → 接收者类型；无法判定时返回 null，调用方负责拒绝请求。
     *
     * <p>商家、骑手角色按{@linkplain #matchesFamily 角色族}匹配：V4__system_rbac.sql 的种子账号
     * 是 MERCHANT_MANAGER / MERCHANT_CLERK，精确 contains 会把它们判成 null，
     * 导致商家端站内信、通知开关、IM 全部返回 10004。
     */
    public static Integer resolve(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        if (roles.contains(Roles.C_USER)) {
            return USER.code;
        }
        if (matchesFamily(roles, Roles.MERCHANT)) {
            return MERCHANT.code;
        }
        if (matchesFamily(roles, Roles.RIDER)) {
            return RIDER.code;
        }
        boolean platform = roles.stream()
                .anyMatch(role -> role != null && role.startsWith(Roles.ADMIN));
        return platform ? PLATFORM.code : null;
    }

    /**
     * 与 {@code RoleAuthInterceptor#matches} 同口径：精确相等，或以 {@code required + "_"} 开头。
     * 不做包含匹配（MERCHANT 不会误判 AMERCHANT）。
     */
    private static boolean matchesFamily(Set<String> roles, String required) {
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            if (role.equals(required) || role.startsWith(required + "_")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValid(Integer code) {
        return code != null && code >= USER.code && code <= PLATFORM.code;
    }
}
