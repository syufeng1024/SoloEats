package com.soloeats.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.report.domain.entity.ReportDailyPlatform;
import com.soloeats.report.mapper.ReportDailyPlatformMapper;
import com.soloeats.report.mapper.ReportSourceMapper;
import com.soloeats.report.mapper.ReportSourceMapper.DashboardTotals;
import com.soloeats.report.mapper.ReportSourceMapper.RepurchaseFact;
import com.soloeats.report.service.ReportAggregateService;
import com.soloeats.report.service.ReportService;
import com.soloeats.report.service.view.DashboardView;
import com.soloeats.report.service.view.OverviewSeries;
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
