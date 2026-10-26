package com.soloeats.report.service;

import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.OverviewSeries;

import java.time.LocalDate;

/**
 * 平台报表查询。阶段A 只提供工作台实时卡与经营总览。
 */
public interface ReportService {

    DashboardView dashboard();

    OverviewSeries overview(LocalDate dateFrom, LocalDate dateTo, String granularity);
}
