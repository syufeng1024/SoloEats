package com.soloeats.member.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * C 端个人资料。phone 只返回脱敏值，任何情况下都不返回明文、密文或哈希。
 */
public record MemberView(
        Long userId,
        String phone,
        String nickname,
        String avatarUrl,
        Integer gender,
        LocalDate birthday,
        Integer status,
        Integer orderCount,
        Long totalAmount,
        LocalDateTime lastOrderAt,
        LocalDateTime cancelApplyAt,
        LocalDateTime registeredAt
) {
}
