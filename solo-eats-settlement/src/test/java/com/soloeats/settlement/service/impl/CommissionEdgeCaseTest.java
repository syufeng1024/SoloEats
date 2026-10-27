package com.soloeats.settlement.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import com.soloeats.settlement.domain.entity.CommissionRule;
import com.soloeats.settlement.domain.entity.FundFlow;
import com.soloeats.settlement.domain.enums.RuleScope;
import com.soloeats.settlement.mapper.CommissionRecordMapper;
import com.soloeats.settlement.mapper.CommissionRuleMapper;
import com.soloeats.settlement.mapper.FundFlowMapper;
import com.soloeats.settlement.service.command.CommissionRuleSaveCommand;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import com.soloeats.settlement.service.view.CommissionRuleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T16 阶段B 抽佣引擎边界与异常路径（QA 独立补充，不复用实现者用例）。
 *
 * <p>覆盖：0 基数、负数基数、HALF_UP 取整边界、uk_order 冲突兜底、生效期倒挂、
 * 全局规则清空 refId。资金流水用真实 {@link FundFlowServiceImpl} 注入，
 * 只 mock Mapper，确保「是否真的落流水」不是被 mock 伪造出来的。
 */
@ExtendWith(MockitoExtension.class)
class CommissionEdgeCaseTest {

    private static final long MERCHANT_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long ORDER_ID = 100L;

    @Mock
    private CommissionRuleMapper commissionRuleMapper;
    @Mock
    private CommissionRecordMapper commissionRecordMapper;
    @Mock
    private FundFlowMapper fundFlowMapper;

    private CommissionServiceImpl commissionService;

    @BeforeEach
    void setUp() {
        FundFlowServiceImpl fundFlowService = new FundFlowServiceImpl(fundFlowMapper);
        commissionService = new CommissionServiceImpl(
                commissionRuleMapper, commissionRecordMapper, fundFlowService);
        ReflectionTestUtils.setField(
                commissionService, "defaultCommissionRate", new BigDecimal("0.0600"));
    }

    @Test
    @DisplayName("抽佣基数为 0（纯优惠单）：抽佣为 0，明细仍落库，但不写 0 元流水")
    void zeroBasePersistsRecordButSkipsFundFlow() {
        when(commissionRecordMapper.insert(any(CommissionRecord.class))).thenAnswer(invocation -> {
            ((CommissionRecord) invocation.getArgument(0)).setId(7001L);
            return 1;
        });

        CommissionResolveResult result = commissionService.resolveAndRecord(order(), 0L);

        assertThat(result.baseAmount()).isZero();
        assertThat(result.commissionAmount()).isZero();
        assertThat(result.usingDefaultRate()).isTrue();

        ArgumentCaptor<CommissionRecord> captor = ArgumentCaptor.forClass(CommissionRecord.class);
        verify(commissionRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getBaseAmount()).isZero();
        assertThat(captor.getValue().getCommissionAmount()).isZero();

        // 0 元流水被 FundFlowServiceImpl 主动跳过：不查重、不落库
        verify(fundFlowMapper, never()).countByBiz(any(), any(), any());
        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("负数基数被钳到 0，不抛异常（结算侧防御）")
    void negativeBaseIsClampedToZero() {
        when(commissionRecordMapper.insert(any(CommissionRecord.class))).thenAnswer(invocation -> {
            ((CommissionRecord) invocation.getArgument(0)).setId(7002L);
            return 1;
        });

        CommissionResolveResult result = commissionService.resolveAndRecord(order(), -500L);

        assertThat(result.baseAmount()).isZero();
        assertThat(result.commissionAmount()).isZero();
    }

    @Test
    @DisplayName("试算入参为负数：抛参数错误，不做任何查询")
    void resolveRejectsNegativeAmount() {
        assertThatThrownBy(() -> commissionService.resolve(MERCHANT_ID, null, null, -1L))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(commissionRuleMapper, never()).findEffectiveGlobal(any(), any());
    }

    @ParameterizedTest(name = "基数 {0} 分 × 0.0600 → 抽佣 {1} 分")
    @CsvSource({
            "1, 0",
            "8, 0",
            "9, 1",
            "25, 2",
            "14700, 882",
            "9999, 600"
    })
    @DisplayName("HALF_UP 取整边界：0.5 分进位、0.49 分舍去")
    void halfUpRoundingBoundaries(long base, long expectedCommission) {
        CommissionResolveResult result = commissionService.resolve(MERCHANT_ID, null, null, base);

        assertThat(result.commissionAmount()).isEqualTo(expectedCommission);
    }

    @Test
    @DisplayName("uk_order 冲突且查不到既有明细：异常向上抛出，不静默吞掉")
    void duplicateWithoutExistingRecordIsRethrown() {
        when(commissionRecordMapper.insert(any(CommissionRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_order"));

        assertThatThrownBy(() -> commissionService.resolveAndRecord(order(), 10_000L))
                .isInstanceOf(DuplicateKeyException.class);

        verify(fundFlowMapper, never()).insert(any(FundFlow.class));
    }

    @Test
    @DisplayName("规则生效期倒挂（from 晚于 to）：拒绝保存，不落库")
    void createRejectsInvertedEffectiveWindow() {
        LocalDateTime from = LocalDateTime.now();
        CommissionRuleSaveCommand command = new CommissionRuleSaveCommand(
                RuleScope.MERCHANT.getCode(), MERCHANT_ID, new BigDecimal("0.0600"),
                null, null, from, from.minusDays(1L), 0, 1, "倒挂规则");

        assertThatThrownBy(() -> commissionService.create(command))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(commissionRuleMapper, never()).insert(any(CommissionRule.class));
    }

    @Test
    @DisplayName("全局规则强制清空 refId，避免产生「scope=1 却带 ref_id」的永不命中规则")
    void createGlobalRuleClearsRefId() {
        CommissionRuleSaveCommand command = new CommissionRuleSaveCommand(
                RuleScope.GLOBAL.getCode(), MERCHANT_ID, new BigDecimal("0.06005"),
                null, null, LocalDateTime.now().minusDays(1L), null, 0, 1, "全局规则");

        CommissionRuleView view = commissionService.create(command);

        ArgumentCaptor<CommissionRule> captor = ArgumentCaptor.forClass(CommissionRule.class);
        verify(commissionRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRefId()).isNull();
        assertThat(captor.getValue().getRuleScope()).isEqualTo(RuleScope.GLOBAL.getCode());
        // 费率统一 4 位小数 HALF_UP：0.06005 → 0.0601
        assertThat(captor.getValue().getCommissionRate()).isEqualByComparingTo("0.0601");
        assertThat(view.refId()).isNull();
    }

    @Test
    @DisplayName("非全局规则缺 refId：拒绝保存")
    void createScopedRuleWithoutRefIdFails() {
        CommissionRuleSaveCommand command = new CommissionRuleSaveCommand(
                RuleScope.MERCHANT.getCode(), null, new BigDecimal("0.0600"),
                null, null, LocalDateTime.now().minusDays(1L), null, 0, 1, "缺 refId");

        assertThatThrownBy(() -> commissionService.create(command))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verify(commissionRuleMapper, never()).insert(any(CommissionRule.class));
    }

    private Order order() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(SHOP_ID);
        return order;
    }
}
