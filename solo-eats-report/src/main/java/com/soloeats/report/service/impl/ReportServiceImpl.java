package com.soloeats.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.report.domain.entity.ReportDailyPlatform;
import com.soloeats.report.mapper.ReportDailyPlatformMapper;
import com.soloeats.report.mapper.ReportSourceMapper;
import com.soloeats.report.mapper.ReportSourceMapper.DashboardTotals;
import com.soloeats.report.mapper.ReportSourceMapper.FundFlowFact;
import com.soloeats.report.mapper.ReportSourceMapper.FunnelTotals;
import com.soloeats.report.mapper.ReportSourceMapper.ProductRankFact;
import com.soloeats.report.mapper.ReportSourceMapper.RefundTotals;
import com.soloeats.report.mapper.ReportSourceMapper.RepurchaseFact;
import com.soloeats.report.mapper.ReportSourceMapper.RiderAnalysisFact;
import com.soloeats.report.mapper.ReportSourceMapper.RiderOnlineFact;
import com.soloeats.report.mapper.ReportSourceMapper.ShopRankFact;
import com.soloeats.report.service.ReportAggregateService;
import com.soloeats.report.service.ReportService;
import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.FinanceReportView;
import com.soloeats.report.service.view.FunnelView;
import com.soloeats.report.service.view.OverviewSeries;
import com.soloeats.report.service.view.ProductRankView;
import com.soloeats.report.service.view.RiderAnalysisView;
import com.soloeats.report.service.view.ShopRankView;
import com.soloeats.report.service.view.UserReportView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 平台报表查询。
 *
 * <p>工作台卡片直查单日订单/用户/骑手表；经营总览按天从 t_report_daily_platform 读取，
 * 当日聚合行缺失时实时计算兜底。所有缺失周期补 0，保证前端折线连续。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_SALE_AMOUNT = "saleAmount";
    private static final String SORT_SALE_QTY = "saleQty";

    private final ReportSourceMapper sourceMapper;
    private final ReportDailyPlatformMapper platformMapper;
    private final ReportAggregateService aggregateService;

    @Override
    public DashboardView dashboard() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        DashboardTotals totals = sourceMapper.dashboardTotals(start, end);
        long gmv = zeroIfNull(totals.todayGmv());
        long orderCount = zeroIfNull(totals.orderCount());
        return new DashboardView(
                gmv,
                orderCount,
                orderCount == 0 ? 0 : gmv / orderCount,
                zeroIfNull(totals.onlineRiders()),
                zeroIfNull(totals.exceptionOrders()),
                zeroIfNull(totals.newUsers()),
                zeroIfNull(totals.inProgressOrders())
        );
    }

    @Override
    public OverviewSeries overview(LocalDate dateFrom, LocalDate dateTo, String granularity) {
        String unit = normalizeGranularity(granularity);
        validateRange(dateFrom, dateTo);

        Map<String, Bucket> buckets = createBuckets(dateFrom, dateTo, unit);

        LocalDate today = LocalDate.now(ZONE);
        boolean todayLoaded = false;
        List<ReportDailyPlatform> rows = platformMapper.selectList(
                new LambdaQueryWrapper<ReportDailyPlatform>()
                        .between(ReportDailyPlatform::getStatDate, dateFrom, dateTo)
                        .orderByAsc(ReportDailyPlatform::getStatDate));
        for (ReportDailyPlatform row : rows) {
            Bucket bucket = buckets.get(label(row.getStatDate(), unit));
            if (bucket != null) {
                bucket.add(row);
            }
            if (row.getStatDate().equals(today)) {
                todayLoaded = true;
            }
        }

        // 当日行由 5 分钟刷新任务维护；尚未生成时实时计算一次，避免总览缺当日数据。
        if (!todayLoaded && !today.isBefore(dateFrom) && !today.isAfter(dateTo)) {
            Bucket bucket = buckets.get(label(today, unit));
            if (bucket != null) {
                bucket.add(aggregateService.buildPlatform(today));
            }
        }

        Map<String, RepurchaseFact> repurchase = new HashMap<>();
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();
        for (RepurchaseFact fact : sourceMapper.repurchaseByPeriod(start, end, dateFormat(unit))) {
            repurchase.put(fact.periodKey(), fact);
        }

        List<OverviewSeries.Point> points = buckets.entrySet().stream()
                .map(entry -> toPoint(entry.getKey(), entry.getValue(), repurchase.get(entry.getKey())))
                .toList();
        return new OverviewSeries(dateFrom, dateTo, unit, points);
    }

    @Override
    public FunnelView funnel(LocalDate dateFrom, LocalDate dateTo) {
        validateReportRange(dateFrom, dateTo);
        FunnelTotals totals = sourceMapper.funnelTotals(dateFrom, dateTo);
        long expose = totals == null ? 0 : zeroIfNull(totals.exposeCount());
        long click = totals == null ? 0 : zeroIfNull(totals.clickCount());
        long cart = totals == null ? 0 : zeroIfNull(totals.cartCount());
        long order = totals == null ? 0 : zeroIfNull(totals.orderCount());
        long pay = totals == null ? 0 : zeroIfNull(totals.payCount());
        return new FunnelView(
                expose,
                click,
                cart,
                order,
                pay,
                percentage(click, expose),
                percentage(cart, click),
                percentage(order, cart),
                percentage(pay, order)
        );
    }

    @Override
    public PageResult<ProductRankView> products(LocalDate dateFrom, LocalDate dateTo,
                                                String sortBy, Long categoryId,
                                                long pageNum, long pageSize) {
        validateReportRange(dateFrom, dateTo);
        String sort = normalizeSortBy(sortBy);
        long currentPage = Math.max(pageNum, 1);
        int size = normalizePageSize(pageSize);

        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();
        long total = sourceMapper.countProductRank(start, end, categoryId);
        if (total == 0) {
            return PageResult.of(List.of(), 0, currentPage, size);
        }

        long offset = (currentPage - 1) * size;
        List<ProductRankFact> facts = SORT_SALE_QTY.equals(sort)
                ? sourceMapper.productRankByQty(start, end, categoryId, size, offset)
                : sourceMapper.productRankByAmount(start, end, categoryId, size, offset);

        List<ProductRankView> list = new ArrayList<>(facts.size());
        for (ProductRankFact fact : facts) {
            list.add(new ProductRankView(
                    fact.spuId(),
                    fact.skuId(),
                    fact.spuName(),
                    fact.categoryId(),
                    zeroIfNull(fact.saleQty()),
                    zeroIfNull(fact.saleAmount()),
                    zeroIfNull(fact.refundQty())
            ));
        }
        return PageResult.of(list, total, currentPage, size);
    }

    @Override
    public List<ShopRankView> shops(LocalDate dateFrom, LocalDate dateTo, int topN) {
        validateReportRange(dateFrom, dateTo);
        int limit = normalizeTopN(topN);
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();

        List<ShopRankView> result = new ArrayList<>();
        for (ShopRankFact fact : sourceMapper.shopRank(start, end, limit)) {
            result.add(new ShopRankView(
                    fact.shopId(),
                    fact.shopName(),
                    fact.merchantId(),
                    zeroIfNull(fact.orderCount()),
                    zeroIfNull(fact.gmv()),
                    scale2(fact.rating()),
                    intValue(fact.avgPrepMinutes()),
                    scale2(fact.cancelRate())
            ));
        }
        return result;
    }

    @Override
    public List<RiderAnalysisView> riders(LocalDate dateFrom, LocalDate dateTo, int topN) {
        validateReportRange(dateFrom, dateTo);
        int limit = normalizeTopN(topN);
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();

        Map<Long, Long> onlineMinutes = new HashMap<>();
        for (RiderOnlineFact online : sourceMapper.riderOnlineMinutes(start, end)) {
            if (online.riderId() != null) {
                onlineMinutes.put(online.riderId(), zeroIfNull(online.onlineMinutes()));
            }
        }

        List<RiderAnalysisView> result = new ArrayList<>();
        for (RiderAnalysisFact fact : sourceMapper.riderAnalysis(start, end, limit)) {
            long deliveryCount = zeroIfNull(fact.deliveryCount());
            long onTimeCount = zeroIfNull(fact.onTimeCount());
            result.add(new RiderAnalysisView(
                    fact.riderId(),
                    fact.riderName(),
                    deliveryCount,
                    onTimeCount,
                    percentage(onTimeCount, deliveryCount),
                    scale2(fact.rating()),
                    zeroIfNull(fact.incomeAmount()),
                    onlineMinutes.getOrDefault(fact.riderId(), 0L),
                    intValue(fact.avgDeliveryMinutes())
            ));
        }
        return result;
    }

    @Override
    public FinanceReportView finance(LocalDate dateFrom, LocalDate dateTo) {
        validateReportRange(dateFrom, dateTo);
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();

        Map<Integer, Long> amounts = new HashMap<>();
        for (FundFlowFact fact : sourceMapper.fundFlowTotals(start, end)) {
            if (fact.flowType() != null) {
                amounts.merge(fact.flowType(), zeroIfNull(fact.amount()), Long::sum);
            }
        }
        long commissionIncome = amounts.getOrDefault(1, 0L);
        long settlementExpense = amounts.getOrDefault(2, 0L);
        long subsidyCost = amounts.getOrDefault(3, 0L);
        long discountCost = amounts.getOrDefault(4, 0L);
        long withdrawExpense = amounts.getOrDefault(5, 0L);
        long refundOffset = amounts.getOrDefault(6, 0L);

        RefundTotals refunds = sourceMapper.refundTotals(start, end);
        long refundOrderCount = refunds == null ? 0 : zeroIfNull(refunds.refundOrderCount());
        int paidOrderCount = sourceMapper.countPaidOrders(start, end);

        return new FinanceReportView(
                commissionIncome,
                settlementExpense,
                subsidyCost,
                discountCost,
                withdrawExpense,
                refundOffset,
                commissionIncome - subsidyCost - discountCost,
                percentage(refundOrderCount, paidOrderCount)
        );
    }

    @Override
    public UserReportView users(LocalDate dateFrom, LocalDate dateTo) {
        validateReportRange(dateFrom, dateTo);
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();

        long activeUserCount = sourceMapper.countActiveUsers(start, end);
        long repurchaseUserCount = sourceMapper.countRepurchaseUsers(start, end);
        return new UserReportView(
                sourceMapper.countNewUsers(start, end),
                activeUserCount,
                repurchaseUserCount,
                percentage(repurchaseUserCount, activeUserCount)
        );
    }

    @Override
    public int rebuild(LocalDate date) {
        if (date == null) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "date 不能为空");
        }
        if (date.isAfter(LocalDate.now(ZONE))) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "date 不能是未来日期");
        }
        return aggregateService.rebuild(date);
    }

    private OverviewSeries.Point toPoint(String period, Bucket bucket, RepurchaseFact repurchase) {
        long buyers = repurchase == null ? 0 : zeroIfNull(repurchase.buyerCount());
        long repeatBuyers = repurchase == null ? 0 : zeroIfNull(repurchase.repeatBuyerCount());
        return new OverviewSeries.Point(
                period,
                bucket.start,
                bucket.end,
                bucket.gmv,
                bucket.orderCount,
                bucket.orderCount == 0 ? 0 : bucket.gmv / bucket.orderCount,
                bucket.refundAmount,
                bucket.refundOrderCount,
                ratio(bucket.refundOrderCount, bucket.paidOrderCount),
                ratio(repeatBuyers, buyers)
        );
    }

    private Map<String, Bucket> createBuckets(LocalDate dateFrom, LocalDate dateTo, String unit) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        LocalDate cursor = periodStart(dateFrom, unit);
        while (!cursor.isAfter(dateTo)) {
            LocalDate end = periodEnd(cursor, unit);
            buckets.put(label(cursor, unit), new Bucket(cursor, end));
            cursor = end.plusDays(1);
        }
        return buckets;
    }

    private static String normalizeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "day";
        }
        String unit = granularity.trim().toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "day", "week", "month" -> unit;
            default -> throw new BizException(ErrorCode.REPORT_DATE_INVALID,
                    "granularity 只支持 day / week / month");
        };
    }

    /**
     * 阶段B 统一日期校验：非空、起止有序、跨度不超过 366 天、截止日不得晚于今天。
     */
    private static void validateReportRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "dateFrom 与 dateTo 不能为空");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "dateFrom 不能晚于 dateTo");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID,
                    "查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        if (dateTo.isAfter(LocalDate.now(ZONE))) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "dateTo 不能是未来日期");
        }
    }

    private static String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return SORT_SALE_AMOUNT;
        }
        String value = sortBy.trim();
        if (SORT_SALE_AMOUNT.equals(value) || SORT_SALE_QTY.equals(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "sortBy 只支持 saleAmount / saleQty");
    }

    /** topN 缺省 10，超出 1~100 时收敛到边界值，避免 LIMIT 过大。 */
    private static int normalizeTopN(int topN) {
        if (topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }

    private static int normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return (int) Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 百分比数值，保留 2 位；分母为 0 时返回 0.00，不产生 NaN/null。 */
    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static int intValue(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "dateFrom 与 dateTo 不能为空");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID, "dateFrom 不能晚于 dateTo");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.REPORT_DATE_INVALID,
                    "查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    private static LocalDate periodStart(LocalDate date, String unit) {
        return switch (unit) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private static LocalDate periodEnd(LocalDate periodStart, String unit) {
        return switch (unit) {
            case "week" -> periodStart.plusDays(6);
            case "month" -> periodStart.with(TemporalAdjusters.lastDayOfMonth());
            default -> periodStart;
        };
    }

    private static String label(LocalDate date, String unit) {
        return switch (unit) {
            case "week" -> {
                WeekFields iso = WeekFields.ISO;
                yield String.format("%d-W%02d",
                        date.get(iso.weekBasedYear()), date.get(iso.weekOfWeekBasedYear()));
            }
            case "month" -> YearMonth.from(date).toString();
            default -> date.toString();
        };
    }

    private static String dateFormat(String unit) {
        return switch (unit) {
            case "week" -> "%x-W%v";
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };
    }

    /** 0~1 比例，保留 4 位；分母为 0 时返回 0.0000。 */
    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static final class Bucket {

        private final LocalDate start;
        private final LocalDate end;
        private long gmv;
        private long orderCount;
        private long paidOrderCount;
        private long refundAmount;
        private int refundOrderCount;

        private Bucket(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }

        private void add(ReportDailyPlatform row) {
            this.gmv += zeroIfNull(row.getGmv());
            this.orderCount += zeroIfNull(row.getOrderCount());
            this.paidOrderCount += zeroIfNull(row.getPaidOrderCount());
            this.refundAmount += zeroIfNull(row.getRefundAmount());
            this.refundOrderCount += zeroIfNull(row.getRefundOrderCount());
        }

        private static long zeroIfNull(Long value) {
            return value == null ? 0L : value;
        }

        private static int zeroIfNull(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
