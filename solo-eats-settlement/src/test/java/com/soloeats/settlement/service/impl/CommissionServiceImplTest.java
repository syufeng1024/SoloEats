package com.soloeats.settlement.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import com.soloeats.settlement.domain.entity.CommissionRule;
import com.soloeats.settlement.domain.enums.RuleScope;
import com.soloeats.settlement.mapper.CommissionRecordMapper;
import com.soloeats.settlement.mapper.CommissionRuleMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.command.CommissionRuleSaveCommand;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三级抽佣规则引擎单测：4 组优先级命中 + 生效期边界 + 无命中回落 + 落库幂等 + 参数校验。
 *
 * <p>生效期边界说明：生效期过滤由 {@code CommissionRuleMapper} 的 SQL 完成
 * （{@code effective_from <= now AND (effective_to IS NULL OR effective_to >= now)}），
 * 纯单测里用「mapper 返回 null（规则尚未生效）」与「mapper 返回规则（已生效）」两种返回
 * 模拟同一商家规则在生效前/生效后的两个边界点，断言引擎的降级与命中行为。
 */
@ExtendWith(MockitoExtension.class)
class CommissionServiceImplTest {

    private static final long MERCHANT_ID = 1L;
    private static final long CATEGORY_ID = 1L;
    private static final long PROMOTION_ID = 9L;
    private static final long AMOUNT = 10_000L;

    @Mock
    private CommissionRuleMapper commissionRuleMapper;
    @Mock
    private CommissionRecordMapper commissionRecordMapper;
    @Mock
    private FundFlowService fundFlowService;

    @InjectMocks
    private CommissionServiceImpl commissionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                commissionService, "defaultCommissionRate", new BigDecimal("0.0600"));
    }

    @Test
    @DisplayName("优先级：活动级命中时不再看商家/品类/全局")
    void promotionRuleWins() {
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.PROMOTION.getCode()), eq(PROMOTION_ID), eq(AMOUNT), any()))
                .thenReturn(rule(40L, RuleScope.PROMOTION, PROMOTION_ID, "0.0500"));

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, PROMOTION_ID, AMOUNT);

        assertThat(result.ruleId()).isEqualTo(40L);
        assertThat(result.ruleScope()).isEqualTo(RuleScope.PROMOTION.getCode());
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0500");
        // 10000 * 0.05 = 500
        assertThat(result.commissionAmount()).isEqualTo(500L);
        assertThat(result.usingDefaultRate()).isFalse();
        verify(commissionRuleMapper, never()).findEffectiveGlobal(any(), any());
    }

    @Test
    @DisplayName("优先级：无活动规则时命中商家级")
    void merchantRuleWinsWhenNoPromotionRule() {
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.PROMOTION.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), eq(MERCHANT_ID), eq(AMOUNT), any()))
                .thenReturn(rule(30L, RuleScope.MERCHANT, MERCHANT_ID, "0.0800"));

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, PROMOTION_ID, AMOUNT);

        assertThat(result.ruleId()).isEqualTo(30L);
        assertThat(result.ruleScope()).isEqualTo(RuleScope.MERCHANT.getCode());
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0800");
        // 10000 * 0.08 = 800
        assertThat(result.commissionAmount()).isEqualTo(800L);
        verify(commissionRuleMapper, never()).findEffectiveGlobal(any(), any());
    }

    @Test
    @DisplayName("优先级：无活动、无商家规则时命中品类级")
    void categoryRuleWinsWhenNoMerchantRule() {
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.PROMOTION.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.CATEGORY.getCode()), eq(CATEGORY_ID), eq(AMOUNT), any()))
                .thenReturn(rule(20L, RuleScope.CATEGORY, CATEGORY_ID, "0.0700"));

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, PROMOTION_ID, AMOUNT);

        assertThat(result.ruleId()).isEqualTo(20L);
        assertThat(result.ruleScope()).isEqualTo(RuleScope.CATEGORY.getCode());
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0700");
        // 10000 * 0.07 = 700
        assertThat(result.commissionAmount()).isEqualTo(700L);
        verify(commissionRuleMapper, never()).findEffectiveGlobal(any(), any());
    }

    @Test
    @DisplayName("优先级：只有全局规则时命中全局，ruleId 不为空")
    void globalRuleWinsWhenNoScopedRule() {
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.PROMOTION.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.CATEGORY.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveGlobal(eq(AMOUNT), any()))
                .thenReturn(rule(10L, RuleScope.GLOBAL, null, "0.0600"));

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, PROMOTION_ID, AMOUNT);

        assertThat(result.ruleId()).isEqualTo(10L);
        assertThat(result.ruleScope()).isEqualTo(RuleScope.GLOBAL.getCode());
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0600");
        assertThat(result.commissionAmount()).isEqualTo(600L);
        assertThat(result.usingDefaultRate()).isFalse();
    }

    @Test
    @DisplayName("无任何规则命中：回落 0.0600 且 rule_id / rule_scope 均为 NULL")
    void fallsBackToDefaultRateWhenNoRuleHit() {
        // 9999 * 0.06 = 599.94 → HALF_UP 到分 = 600
        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, PROMOTION_ID, 9_999L);

        assertThat(result.ruleId()).isNull();
        assertThat(result.ruleScope()).isNull();
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0600");
        assertThat(result.baseAmount()).isEqualTo(9_999L);
        assertThat(result.commissionAmount()).isEqualTo(600L);
        assertThat(result.usingDefaultRate()).isTrue();
    }

    @Test
    @DisplayName("生效期边界：规则尚未生效（effective_from 在未来）时按未命中降级")
    void ruleNotEffectiveBeforeEffectiveFrom() {
        // 商家规则 effective_from = now + 1 天，SQL 过滤后 mapper 返回 null
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), eq(MERCHANT_ID), eq(AMOUNT), any()))
                .thenReturn(null);

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, null, AMOUNT);

        assertThat(result.ruleId()).isNull();
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0600");
        assertThat(result.commissionAmount()).isEqualTo(600L);
    }

    @Test
    @DisplayName("生效期边界：规则已生效（effective_from 在过去）时正常命中商家级")
    void ruleEffectiveAfterEffectiveFrom() {
        LocalDateTime effectiveFrom = LocalDateTime.now().minusDays(1L);
        CommissionRule rule = rule(30L, RuleScope.MERCHANT, MERCHANT_ID, "0.0800");
        rule.setEffectiveFrom(effectiveFrom);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), eq(MERCHANT_ID), eq(AMOUNT), any()))
                .thenReturn(rule);

        CommissionResolveResult result = commissionService.resolve(
                MERCHANT_ID, CATEGORY_ID, null, AMOUNT);

        assertThat(result.ruleId()).isEqualTo(30L);
        assertThat(result.commissionRate()).isEqualByComparingTo("0.0800");
        assertThat(result.commissionAmount()).isEqualTo(800L);
    }

    @Test
    @DisplayName("结算落库：命中规则写 t_commission_record 并记佣金收入流水")
    void resolveAndRecordPersistsRecordAndFundFlow() {
        when(commissionRuleMapper.findOrderCategoryId(100L)).thenReturn(CATEGORY_ID);
        when(commissionRuleMapper.findOrderPromotionId(100L)).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.MERCHANT.getCode()), any(), any(), any())).thenReturn(null);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.CATEGORY.getCode()), eq(CATEGORY_ID), eq(AMOUNT), any()))
                .thenReturn(rule(20L, RuleScope.CATEGORY, CATEGORY_ID, "0.0700"));
        when(commissionRecordMapper.insert(any(CommissionRecord.class))).thenAnswer(invocation -> {
            ((CommissionRecord) invocation.getArgument(0)).setId(900L);
            return 1;
        });

        CommissionResolveResult result = commissionService.resolveAndRecord(order(100L), AMOUNT);

        assertThat(result.ruleId()).isEqualTo(20L);
        assertThat(result.commissionAmount()).isEqualTo(700L);

        ArgumentCaptor<CommissionRecord> captor = ArgumentCaptor.forClass(CommissionRecord.class);
        verify(commissionRecordMapper).insert(captor.capture());
        CommissionRecord saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(100L);
        assertThat(saved.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(saved.getShopId()).isEqualTo(1L);
        assertThat(saved.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(saved.getBaseAmount()).isEqualTo(AMOUNT);
        assertThat(saved.getRuleId()).isEqualTo(20L);
        assertThat(saved.getRuleScope()).isEqualTo(RuleScope.CATEGORY.getCode());
        assertThat(saved.getCommissionRate()).isEqualByComparingTo("0.0700");
        assertThat(saved.getCommissionAmount()).isEqualTo(700L);

        verify(fundFlowService).recordCommissionIncome(saved);
    }

    @Test
    @DisplayName("结算落库幂等：uk_order 冲突时返回已落库明细，不再记流水")
    void resolveAndRecordIsIdempotentOnDuplicateOrder() {
        when(commissionRuleMapper.findOrderCategoryId(100L)).thenReturn(null);
        when(commissionRuleMapper.findOrderPromotionId(100L)).thenReturn(PROMOTION_ID);
        when(commissionRuleMapper.findEffectiveByScopeAndRef(
                eq(RuleScope.PROMOTION.getCode()), eq(PROMOTION_ID), eq(AMOUNT), any()))
                .thenReturn(rule(40L, RuleScope.PROMOTION, PROMOTION_ID, "0.0500"));
        when(commissionRecordMapper.insert(any(CommissionRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_order"));

        CommissionRecord existing = new CommissionRecord();
        existing.setId(901L);
        existing.setOrderId(100L);
        existing.setBaseAmount(AMOUNT);
        existing.setRuleId(40L);
        existing.setRuleScope(RuleScope.PROMOTION.getCode());
        existing.setCommissionRate(new BigDecimal("0.0500"));
        existing.setCommissionAmount(500L);
        when(commissionRecordMapper.findByOrderId(100L)).thenReturn(existing);

        CommissionResolveResult result = commissionService.resolveAndRecord(order(100L), AMOUNT);

        assertThat(result.ruleId()).isEqualTo(40L);
        assertThat(result.commissionAmount()).isEqualTo(500L);
        verify(fundFlowService, never()).recordCommissionIncome(any());
    }

    @Test
    @DisplayName("按 id 查不到规则：抛 60003")
    void updateMissingRuleThrowsRuleNotFound() {
        when(commissionRuleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commissionService.update(999L, command("0.0600")))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.COMMISSION_RULE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("费率越界：commissionRate 大于 1 直接抛参数错误，不落库")
    void createRejectsOutOfRangeRate() {
        assertThatThrownBy(() -> commissionService.create(command("1.5000")))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        verify(commissionRuleMapper, never()).insert(any(CommissionRule.class));
    }

    private CommissionRule rule(Long id, RuleScope scope, Long refId, String rate) {
        CommissionRule rule = new CommissionRule();
        rule.setId(id);
        rule.setRuleScope(scope.getCode());
        rule.setRefId(refId);
        rule.setCommissionRate(new BigDecimal(rate));
        rule.setPriority(0);
        rule.setStatus(1);
        rule.setDeleted(0);
        rule.setEffectiveFrom(LocalDateTime.now().minusDays(1L));
        return rule;
    }

    private Order order(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        return order;
    }

    private CommissionRuleSaveCommand command(String rate) {
        return new CommissionRuleSaveCommand(
                RuleScope.MERCHANT.getCode(), MERCHANT_ID, new BigDecimal(rate),
                null, null, LocalDateTime.now().minusDays(1L), null,
                0, 1, "单测规则");
    }
}
