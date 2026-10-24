package com.soloeats.member.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.member.domain.enums.WalletBizType;
import com.soloeats.member.service.query.WalletLogQuery;
import com.soloeats.member.service.view.WalletLogView;
import com.soloeats.member.service.view.WalletView;

/**
 * C 端钱包。余额变动一律走原子条件更新 + 同事务流水，禁止先查余额再更新。
 */
public interface MemberWalletService {

    /** 钱包不存在抛 80001；四个金额字段单位均为分。 */
    WalletView getWallet(Long userId);

    /** 流水分页，pageNum 从 1 开始，pageSize 上限 100。 */
    PageResult<WalletLogView> listLogs(Long userId, WalletLogQuery query, long pageNum, long pageSize);

    /**
     * 模拟充值。[1, 100000] 分，同 requestId 只入账一次。
     * app.wallet.mock-recharge-enabled=false 时抛 80007。
     */
    WalletView recharge(Long userId, Long amount, String requestId);

    /**
     * 原子扣减。返回 0 时重查 version 重试，最多 3 次仍失败抛 80006；
     * bizType=CONSUME 时同事务累加 total_consume。
     */
    void deduct(Long userId, Long amount, WalletBizType bizType, Long bizId, String requestId);

    /**
     * 后台手工调账。amount 可负，|amount| <= 100000 分；同 requestId 幂等；
     * 流水 bizType=ADJUST，remark 记「操作人：原因」。
     */
    WalletView adjust(Long userId, Long amount, String reason, String requestId,
                      Long operatorId, String operatorName);
}
