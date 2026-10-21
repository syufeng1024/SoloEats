package com.soloeats.settlement.service;

import com.soloeats.settlement.service.view.SettlementDetailResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import com.soloeats.settlement.service.view.SettlementListResult;

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
}
