package com.soloeats.report.service;

import com.soloeats.report.domain.entity.ReportDailyPlatform;

import java.time.LocalDate;

/**
 * 日报预聚合。由 ReportAggregateJob 调度；写操作只落在 t_report_* 表。
 */
public interface ReportAggregateService {

    /**
     * 按 statDate 重算平台日报与门店日报。重复调用结果一致（先删后插，同一事务）。
     */
    void aggregate(LocalDate statDate);

    /**
     * 只计算不落库，供 overview 在当日聚合行缺失时兜底。
     */
    ReportDailyPlatform buildPlatform(LocalDate statDate);
}
