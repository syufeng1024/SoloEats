package com.soloeats.settlement.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.settlement.domain.entity.FundFlow;
import com.soloeats.settlement.domain.enums.FlowType;
import com.soloeats.settlement.mapper.FundFlowMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.view.FundFlowSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T16 阶段B 资金流水幂等、0 元取舍与利润口径（QA 独立补充）。
 */
@ExtendWith(MockitoExtension.class)
class FundFlowEdgeCaseTest {

    private static final String BIZ_TYPE = FundFlowService.BIZ_TYPE_SETTLEMENT_BILL;

    @Mock
    private FundFlowMapper fundFlowMapper;

    private FundFlowServiceImpl fundFlowService;

    @BeforeEach
    void setUp() {
        fundFlowService = new FundFlowServiceImpl(fundFlowMapper);
    }

    @Test
    @DisplayName("流水金额为负：参数错误")
    void negativeAmountRejected() {
        assertThatThrownBy(() -> fundFlowService.record(
                FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L,
                FlowType.DIRECTION_OUT, -1L, "负金额"))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("0 元流水：直接跳过，不查重也不落库（0 元单据打款不留痕）")
    void zeroAmountSkippedWithoutQuery() {
        fundFlowService.record(FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L,
                FlowType.DIRECTION_OUT, 0L, "0 元结算单");

        verify(fundFlowMapper, never()).countByBiz(any(), any(), any());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("同一 (flowType,bizType,bizId) 已记账：跳过写入")
    void existingBizIsSkipped() {
        when(fundFlowMapper.countByBiz(
                FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L)).thenReturn(1);

        fundFlowService.record(FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L,
                FlowType.DIRECTION_OUT, 14_118L, "重复记账");

        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("并发下预查都通过：uk_biz 冲突被吞掉，不向上抛")
    void duplicateKeyIsSwallowedByIdempotency() {
        when(fundFlowMapper.countByBiz(any(), any(), any())).thenReturn(0);
        when(fundFlowMapper.insert(any(FundFlow.class)))
                .thenThrow(new DuplicateKeyException("uk_biz"));

        assertThatCode(() -> fundFlowService.record(
                FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L,
                FlowType.DIRECTION_OUT, 14_118L, "并发重复"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非法 flowType / direction / bizType：参数错误")
    void invalidEnumArgumentsRejected() {
        assertThatThrownBy(() -> fundFlowService.record(
                99, BIZ_TYPE, 1L, FlowType.DIRECTION_OUT, 100L, null))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        assertThatThrownBy(() -> fundFlowService.record(
                FlowType.SETTLEMENT_EXPENSE.getCode(), " ", 1L,
                FlowType.DIRECTION_OUT, 100L, null))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        assertThatThrownBy(() -> fundFlowService.record(
                FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 0L,
                FlowType.DIRECTION_OUT, 100L, null))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        assertThatThrownBy(() -> fundFlowService.record(
                FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L, 3, 100L, null))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("备注超 255 字符被截断，避免 SQL 报错")
    void longRemarkIsTruncated() {
        when(fundFlowMapper.countByBiz(any(), any(), any())).thenReturn(0);
        when(fundFlowMapper.insert(any(FundFlow.class))).thenReturn(1);

        fundFlowService.record(FlowType.SETTLEMENT_EXPENSE.getCode(), BIZ_TYPE, 1L,
                FlowType.DIRECTION_OUT, 100L, "长".repeat(300));

        ArgumentCaptor<FundFlow> captor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(captor.capture());
        assertThat(captor.getValue().getRemark()).hasSize(255);
    }

    @Test
    @DisplayName("汇总区间使用左闭右开，且 profit = 佣金收入 − 结算支出 − 补贴支出 − 提现支出")
    void summaryProfitFormula() {
        Map<String, Object> row = new HashMap<>();
        row.put("COMMISSION_INCOME", 882L);
        row.put("settlement_expense", 14_118L);
        row.put("subsidy_cost", 500L);
        row.put("discount_cost", 300L);
        row.put("payout_expense", 6_000L);
        row.put("refund_offset", 1_000L);
        when(fundFlowMapper.sumByFlowType(any(), any())).thenReturn(row);

        FundFlowSummary summary = fundFlowService.summary(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));

        assertThat(summary.commissionIncome()).isEqualTo(882L);
        assertThat(summary.settlementExpense()).isEqualTo(14_118L);
        assertThat(summary.subsidyCost()).isEqualTo(500L);
        assertThat(summary.payoutExpense()).isEqualTo(6_000L);
        // 882 - 14118 - 500 - 6000 = -19736；discountCost / refundOffset 只展示不参与
        assertThat(summary.profit()).isEqualTo(-19_736L);
    }

    @Test
    @DisplayName("汇总区间：dateTo 当天含在内（右端取次日 00:00 的开区间）")
    void summaryRangeIsInclusiveOfEndDate() {
        when(fundFlowMapper.sumByFlowType(any(), any())).thenReturn(Map.of());

        fundFlowService.summary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fundFlowMapper).sumByFlowType(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 28, 0, 0));
    }

    @Test
    @DisplayName("无流水时返回全 0 汇总，不是 null")
    void emptySummaryWhenNoRow() {
        when(fundFlowMapper.sumByFlowType(any(), any())).thenReturn(null);

        FundFlowSummary summary = fundFlowService.summary(null, null);

        assertThat(summary.commissionIncome()).isZero();
        assertThat(summary.profit()).isZero();
    }

    @Test
    @DisplayName("区间倒挂：参数错误")
    void invertedRangeRejected() {
        assertThatThrownBy(() -> fundFlowService.summary(
                LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 1)))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }
}
