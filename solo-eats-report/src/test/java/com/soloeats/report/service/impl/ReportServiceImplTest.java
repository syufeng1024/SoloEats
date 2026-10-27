package com.soloeats.report.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.report.mapper.ReportDailyPlatformMapper;
import com.soloeats.report.mapper.ReportSourceMapper;
import com.soloeats.report.mapper.ReportSourceMapper.FunnelTotals;
import com.soloeats.report.mapper.ReportSourceMapper.RefundTotals;
import com.soloeats.report.mapper.ReportSourceMapper.RiderAnalysisFact;
import com.soloeats.report.service.ReportAggregateService;
import com.soloeats.report.service.view.FinanceReportView;
import com.soloeats.report.service.view.FunnelView;
import com.soloeats.report.service.view.RiderAnalysisView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 1);

    @Mock
    private ReportSourceMapper sourceMapper;

    @Mock
    private ReportDailyPlatformMapper platformMapper;

    @Mock
    private ReportAggregateService aggregateService;

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(sourceMapper, platformMapper, aggregateService);
    }

    @Test
    @DisplayName("空数据财务：各项金额与退款率均为 0，退款率不是 NaN")
    void emptyFinanceReturnsZeros() {
        when(sourceMapper.fundFlowTotals(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(sourceMapper.refundTotals(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new RefundTotals(0L, 0L));
        when(sourceMapper.countPaidOrders(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        FinanceReportView view = reportService.finance(DAY, DAY);

        assertThat(view.commissionIncome()).isZero();
        assertThat(view.settlementExpense()).isZero();
        assertThat(view.subsidyCost()).isZero();
        assertThat(view.discountCost()).isZero();
        assertThat(view.withdrawExpense()).isZero();
        assertThat(view.refundOffset()).isZero();
        assertThat(view.grossProfit()).isZero();
        assertThat(view.refundRate()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(view.refundRate()).isNotNull();
    }

    @Test
    @DisplayName("日期非法：dateFrom 晚于 dateTo 抛 100010")
    void reversedDateRangeRejected() {
        assertThatThrownBy(() -> reportService.funnel(DAY.plusDays(1), DAY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.REPORT_DATE_INVALID.getCode());
    }

    @Test
    @DisplayName("排序非法：sortBy 不在白名单抛 10001")
    void invalidSortByRejected() {
        assertThatThrownBy(() -> reportService.products(DAY, DAY, "skuName", null, 1, 20))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("漏斗转化率：1000/100/20/10/8 → 10% / 20% / 50% / 80%")
    void funnelRatesAreComputedFromAdjacentStages() {
        when(sourceMapper.funnelTotals(DAY, DAY))
                .thenReturn(new FunnelTotals(1000L, 100L, 20L, 10L, 8L));

        FunnelView view = reportService.funnel(DAY, DAY);

        assertThat(view.exposeCount()).isEqualTo(1000L);
        assertThat(view.clickCount()).isEqualTo(100L);
        assertThat(view.cartCount()).isEqualTo(20L);
        assertThat(view.orderCount()).isEqualTo(10L);
        assertThat(view.payCount()).isEqualTo(8L);
        assertThat(view.clickRate()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(view.cartRate()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(view.orderRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(view.payRate()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("骑手准时率：3 单中 2 单准时 → 66.67%")
    void riderOnTimeRateRoundsToTwoDecimals() {
        when(sourceMapper.riderAnalysis(any(LocalDateTime.class), any(LocalDateTime.class), eq(10)))
                .thenReturn(List.of(new RiderAnalysisFact(
                        9L, "骑手A", 3L, 2L, new BigDecimal("4.80"), 3600L, 25L)));

        List<RiderAnalysisView> riders = reportService.riders(DAY, DAY, 10);

        assertThat(riders).hasSize(1);
        RiderAnalysisView view = riders.get(0);
        assertThat(view.riderId()).isEqualTo(9L);
        assertThat(view.deliveryCount()).isEqualTo(3L);
        assertThat(view.onTimeCount()).isEqualTo(2L);
        assertThat(view.onTimeRate()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(view.rating()).isEqualByComparingTo(new BigDecimal("4.80"));
        assertThat(view.incomeAmount()).isEqualTo(3600L);
        assertThat(view.avgDeliveryMinutes()).isEqualTo(25);
    }
}
