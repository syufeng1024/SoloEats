package com.soloeats.member.domain.enums;

/**
 * 钱包流水业务类型。落库为 varchar(16)，存枚举名（name）。
 */
public enum WalletBizType {

    /** 充值入账 */
    RECHARGE,
    /** 余额消费 */
    CONSUME,
    /** 退款回退 */
    REFUND,
    /** 后台手工调账 */
    ADJUST,
    /** 提现出账 */
    WITHDRAW,
    /** 冻结 */
    FREEZE,
    /** 解冻 */
    UNFREEZE
}
