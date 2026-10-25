package com.soloeats.settlement.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.settlement.service.view.PayoutView;

import java.time.LocalDate;

/**
 * 商家/骑手提现：申请、审核、模拟打款、到账登记。
 * subjectType 与 t_payout_order.subject_type 对齐：1 商家 2 骑手。
 */
public interface PayoutService {

    int SUBJECT_TYPE_MERCHANT = 1;
    int SUBJECT_TYPE_RIDER = 2;

    /**
     * 发起提现。金额单位分，必须大于 0 且不超过单笔上限，且不超过可提现余额。
     */
    PayoutView apply(Integer subjectType, Long subjectId, Long amount, Integer channel);

    /**
     * 我的提现记录（商家/骑手端），只返回本人主体。
     */
    PageResult<PayoutView> pageMine(Integer subjectType, Long subjectId, Integer status,
                                    long pageNum, long pageSize);

    /**
     * 平台提现列表，可按主体类型、主体 ID、状态、申请日期筛选。
     */
    PageResult<PayoutView> adminPage(Integer subjectType, Long subjectId, Integer status,
                                     LocalDate dateFrom, LocalDate dateTo,
                                     long pageNum, long pageSize);

    /**
     * 审核：仅待审核可审，通过 → 10，驳回 → 40 并写审核意见。
     */
    void audit(Long id, boolean pass, String remark, Long auditorId);

    /**
     * 模拟打款：仅审核通过（10）可打款 → 20，写 paid_at。
     */
    void pay(Long id);

    /**
     * 登记到账：仅打款中（20）可到账 → 30。
     */
    void arrive(Long id);

    /**
     * 当前可提现余额（分）= 收入基数 − 未驳回提现占用。
     */
    long availableBalance(Integer subjectType, Long subjectId);
}
