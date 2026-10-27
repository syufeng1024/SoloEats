package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.PointAccount;

import java.time.LocalDateTime;

/**
 * 积分账户视图。全部为整数，不出现浮点。
 */
public record PointAccountView(
        Long userId,
        Integer balance,
        Long totalEarned,
        Long totalConsumed,
        LocalDateTime updatedAt
) {

    public static PointAccountView from(PointAccount account) {
        return new PointAccountView(
                account.getUserId(),
                account.getBalance() == null ? 0 : account.getBalance(),
                account.getTotalEarned() == null ? 0L : account.getTotalEarned(),
                account.getTotalConsumed() == null ? 0L : account.getTotalConsumed(),
                account.getUpdatedAt());
    }
}
