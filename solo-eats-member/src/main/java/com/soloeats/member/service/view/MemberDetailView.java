package com.soloeats.member.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台会员详情：在 {@link MemberView} 基础上补地址数、钱包余额（分）与标签名。
 * phone 仍只取掩码，不返回明文、密文或哈希。
 */
public record MemberDetailView(
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
        LocalDateTime registeredAt,
        long addressCount,
        long walletBalance,
        List<String> tagNames
) {

    public static MemberDetailView of(MemberView member, long addressCount, long walletBalance,
                                      List<String> tagNames) {
        return new MemberDetailView(
                member.userId(),
                member.phone(),
                member.nickname(),
                member.avatarUrl(),
                member.gender(),
                member.birthday(),
                member.status(),
                member.orderCount(),
                member.totalAmount(),
                member.lastOrderAt(),
                member.cancelApplyAt(),
                member.registeredAt(),
                addressCount,
                walletBalance,
                tagNames == null ? List.of() : tagNames);
    }
}
