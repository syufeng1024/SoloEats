package com.soloeats.app.vo;

/**
 * 骑手可提现余额。availableBalance 单位为分，
 * 口径由 PayoutService#availableBalance 统一计算（收入基数 − 未驳回提现占用）。
 */
public record RiderBalanceView(long availableBalance) {
}
