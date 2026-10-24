package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.MemberWallet;

/** 钱包概览，金额单位分。 */
public record WalletView(
        long balance,
        long frozenAmount,
        long totalRecharge,
        long totalConsume
) {

    public static WalletView from(MemberWallet wallet) {
        return new WalletView(
                nullSafe(wallet.getBalance()),
                nullSafe(wallet.getFrozenAmount()),
                nullSafe(wallet.getTotalRecharge()),
                nullSafe(wallet.getTotalConsume()));
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
