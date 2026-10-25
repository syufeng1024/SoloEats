package com.soloeats.settlement.service;

import com.soloeats.settlement.service.view.AdminSettlementStatsResult;
import com.soloeats.settlement.service.view.SettlementDetailResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import com.soloeats.settlement.service.view.SettlementListResult;

import java.time.LocalDate;

public interface SettlementService {

    /**
     * 归集 COMPLETED、已过结算等待期、未退款且未结算的订单，按商家生成结算账单。
     */
    SettlementGenerateResult generateDailyBills();

    /**
     * 商家结算账单列表与汇总。
     */
    SettlementListResult listMerchantBills(Long merchantId, Integer status);

    /**
     * 商家结算账单明细与关联订单。
     */
    SettlementDetailResult getBillDetail(Long merchantId, Long billId);

    /**
     * 平台结算统计：跨商家账单分页 + 同一筛选条件下的佣金与应结汇总。
     */
    AdminSettlementStatsResult adminStats(Long merchantId, Integer status,
                                          LocalDate dateFrom, LocalDate dateTo,
                                          long pageNum, long pageSize);

    /**
     * 审核结算单：仅待审核（0）可审，通过 → 10 并记审核人/时间，驳回 → 40。
     * remark 落应用日志与操作日志（t_settlement_bill 无审核意见列，本阶段不加列）。
     */
    void auditBill(Long billId, boolean pass, String remark, Long auditorId);

    /**
     * 模拟打款：仅审核通过（10）可打款 → 30，写 paid_at。不接真实支付通道。
     */
    void payBill(Long billId, Long operatorId);
}
