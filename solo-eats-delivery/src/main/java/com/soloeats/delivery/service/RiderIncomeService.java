package com.soloeats.delivery.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.service.view.RiderIncomeDetailView;
import com.soloeats.delivery.service.view.RiderIncomeOverview;

import java.time.LocalDate;

/**
 * 骑手收入。金额单位分，按 t_rider_income.settlement_date 聚合。
 */
public interface RiderIncomeService {

    RiderIncomeOverview overview(Long riderId);

    /** 单笔收入构成，按 settlement_date 倒序；dateFrom/dateTo 为闭区间且可空。 */
    PageResult<RiderIncomeDetailView> details(Long riderId, LocalDate dateFrom, LocalDate dateTo,
                                              long pageNum, long pageSize);

    /**
     * 送达后结算骑手收入：优先骑手专属费率，否则全局费率。
     * 已存在 t_rider_income 行时原地更新，不存在时插入（uk_delivery 兜底并发）。
     */
    void settle(Long deliveryId);
}
