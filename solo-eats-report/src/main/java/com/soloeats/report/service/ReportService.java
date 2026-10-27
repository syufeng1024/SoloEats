package com.soloeats.report.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.FinanceReportView;
import com.soloeats.report.service.view.FunnelView;
import com.soloeats.report.service.view.OverviewSeries;
import com.soloeats.report.service.view.ProductRankView;
import com.soloeats.report.service.view.RiderAnalysisView;
import com.soloeats.report.service.view.ShopRankView;
import com.soloeats.report.service.view.UserReportView;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台报表查询。工作台与经营总览读预聚合表；排行榜类实时聚合业务表，
 * 保证历史区间在聚合未回放时也能出数。
 */
public interface ReportService {

    DashboardView dashboard();

    OverviewSeries overview(LocalDate dateFrom, LocalDate dateTo, String granularity);

    /** 流量转化漏斗：五级量与四级转化率，区间无数据时返回全 0。 */
    FunnelView funnel(LocalDate dateFrom, LocalDate dateTo);

    /** 商品榜。sortBy 只支持 saleAmount / saleQty。 */
    PageResult<ProductRankView> products(LocalDate dateFrom, LocalDate dateTo, String sortBy,
                                         Long categoryId, long pageNum, long pageSize);

    /** 门店排行，topN 限定 1~100。 */
    List<ShopRankView> shops(LocalDate dateFrom, LocalDate dateTo, int topN);

    /** 骑手分析，topN 限定 1~100。 */
    List<RiderAnalysisView> riders(LocalDate dateFrom, LocalDate dateTo, int topN);

    /** 财务报表：佣金、结算支出、补贴/优惠成本、提现、退款冲抵、毛利润与退款率。 */
    FinanceReportView finance(LocalDate dateFrom, LocalDate dateTo);

    /** 用户报表：新增、活跃、复购用户数与复购率。 */
    UserReportView users(LocalDate dateFrom, LocalDate dateTo);

    /** 重算指定日全部 5 张聚合表，返回写入行数。 */
    int rebuild(LocalDate date);
}
