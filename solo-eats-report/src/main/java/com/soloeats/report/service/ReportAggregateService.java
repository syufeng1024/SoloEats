package com.soloeats.report.service;

import com.soloeats.report.domain.entity.ReportDailyPlatform;
import com.soloeats.report.domain.entity.ReportDailyRider;
import com.soloeats.report.domain.entity.ReportDailySpu;
import com.soloeats.report.domain.entity.ReportFunnelDaily;

import java.time.LocalDate;
import java.util.List;

/**
 * 日报预聚合。由 ReportAggregateJob 调度；写操作只落在 t_report_* 表。
 */
public interface ReportAggregateService {

    /**
     * 按 statDate 重算平台、门店、商品、骑手、漏斗 5 张日报。重复调用结果一致。
     */
    void aggregate(LocalDate statDate);

    /**
     * 只计算不落库，供 overview 在当日聚合行缺失时兜底。
     */
    ReportDailyPlatform buildPlatform(LocalDate statDate);

    /** 商品销售日报，只计算不落库。 */
    List<ReportDailySpu> buildSpus(LocalDate statDate);

    /** 骑手配送日报，只计算不落库。 */
    List<ReportDailyRider> buildRiders(LocalDate statDate);

    /** 流量漏斗日报，只计算不落库。 */
    ReportFunnelDaily buildFunnel(LocalDate statDate);

    /**
     * 重算指定日全部 5 张日报（先删后插，同一事务），返回写入行数。
     */
    int rebuild(LocalDate statDate);
}
