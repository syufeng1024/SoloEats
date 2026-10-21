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
}
