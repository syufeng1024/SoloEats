package com.soloeats.merchant.support;

/**
 * 文件操作者。两种形态互斥：
 * 1. 已登录用户（userId / username / admin）；
 * 2. 入驻申请人（applyId），此时 userId 为空，t_file.biz_id 绑定 applyId。
 */
public record FileActor(Long userId, String username, boolean admin, Long merchantId, Long applyId) {

    public static FileActor user(Long userId, String username, boolean admin, Long merchantId) {
        return new FileActor(userId, username, admin, merchantId, null);
    }

    public static FileActor apply(Long applyId) {
        return new FileActor(null, "APPLY", false, null, applyId);
    }

    public boolean isApply() {
        return applyId != null;
    }

    public String uploaderType() {
        if (isApply()) {
            return "APPLY";
        }
        return admin ? "ADMIN" : "USER";
    }
}
