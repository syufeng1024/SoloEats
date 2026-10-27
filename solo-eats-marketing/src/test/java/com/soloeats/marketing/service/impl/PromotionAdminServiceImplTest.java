package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.domain.entity.PromotionScope;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.mapper.PromotionScopeMapper;
import com.soloeats.marketing.service.command.PromotionSaveCommand;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionAdminServiceImplTest {

    private static final long PROMOTION_ID = 100L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 1, 0, 0);

    @Mock
    private PromotionMapper promotionMapper;

    @Mock
    private PromotionRuleMapper promotionRuleMapper;

    @Mock
    private PromotionScopeMapper promotionScopeMapper;

    @InjectMocks
    private PromotionAdminServiceImpl promotionAdminService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，Lambda 列名解析需要显式注册实体列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Promotion.class);
        TableInfoHelper.initTableInfo(assistant, PromotionRule.class);
        TableInfoHelper.initTableInfo(assistant, PromotionScope.class);
    }

    // ---------------------------------------------------------------
    // 新建
    // ---------------------------------------------------------------

    @Test
    @DisplayName("新建：2 档规则 + 1 条品类范围全部按 promotion_id 落子表，主表落草稿态")
    void createPersistsRulesAndScopes() {
        when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
            ((Promotion) invocation.getArgument(0)).setId(PROMOTION_ID);
            return 1;
        });

        Long id = promotionAdminService.create(ladderCommand());

        assertThat(id).isEqualTo(PROMOTION_ID);

        ArgumentCaptor<Promotion> promotionCaptor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionMapper).insert(promotionCaptor.capture());
        Promotion saved = promotionCaptor.getValue();
        assertThat(saved.getPromotionName()).isEqualTo("阶梯满减");
        assertThat(saved.getPromotionType()).isEqualTo(1);
        assertThat(saved.getScopeType()).isEqualTo(4);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getUsedBudget()).isZero();
        assertThat(saved.getPriority()).isEqualTo(200);
        assertThat(saved.getTotalBudget()).isEqualTo(100000L);

        ArgumentCaptor<PromotionRule> ruleCaptor = ArgumentCaptor.forClass(PromotionRule.class);
        verify(promotionRuleMapper, times(2)).insert(ruleCaptor.capture());
        assertThat(ruleCaptor.getAllValues())
                .extracting(PromotionRule::getPromotionId)
                .containsOnly(PROMOTION_ID);
        assertThat(ruleCaptor.getAllValues())
                .extracting(PromotionRule::getThresholdAmount)
                .containsExactly(10000L, 20000L);
        assertThat(ruleCaptor.getAllValues())
                .extracting(PromotionRule::getDiscountAmount)
                .containsExactly(1000L, 3000L);
        assertThat(ruleCaptor.getAllValues())
                .extracting(PromotionRule::getSort)
                .containsExactly(1, 2);

        ArgumentCaptor<PromotionScope> scopeCaptor = ArgumentCaptor.forClass(PromotionScope.class);
        verify(promotionScopeMapper).insert(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().getPromotionId()).isEqualTo(PROMOTION_ID);
        assertThat(scopeCaptor.getValue().getRefType()).isEqualTo(3);
        assertThat(scopeCaptor.getValue().getRefId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("新建折扣活动：discountRate 按 decimal(4,2) 精度 HALF_UP 归一，discountAmount 置空")
    void createDiscountPromotionNormalizesRate() {
        when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
            ((Promotion) invocation.getArgument(0)).setId(PROMOTION_ID);
            return 1;
        });

        promotionAdminService.create(new PromotionSaveCommand(
                2, "8.5折", 1, null, null, 1, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(
                        5000L, 999L, new BigDecimal("0.8549"), 800L, 1)),
                List.of()));

        ArgumentCaptor<PromotionRule> captor = ArgumentCaptor.forClass(PromotionRule.class);
        verify(promotionRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiscountRate()).isEqualTo(new BigDecimal("0.85"));
        assertThat(captor.getValue().getMaxDiscount()).isEqualTo(800L);
        assertThat(captor.getValue().getDiscountAmount()).isNull();
    }

    @Test
    @DisplayName("满减活动折扣率越界：拒绝新建，不落主表")
    void createRejectsInvalidDiscountRate() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                2, "错误折扣", 1, null, null, 1, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(
                        5000L, null, new BigDecimal("1.00"), null, 1)),
                List.of());

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    @Test
    @DisplayName("满减档位金额为 0：拒绝新建")
    void createRejectsNonPositiveDiscountAmount() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                1, "零元满减", 1, null, null, 1, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(3000L, 0L, null, null, 1)),
                List.of());

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    @Test
    @DisplayName("scopeType=2 缺少 merchantId：拒绝新建")
    void createRejectsMerchantScopeWithoutMerchantId() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                1, "商家满减", 2, null, null, 2, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(3000L, 500L, null, null, 1)),
                List.of());

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    @Test
    @DisplayName("scopeType=4 缺少范围项：拒绝新建")
    void createRejectsCategoryScopeWithoutRefs() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                1, "品类满减", 1, null, null, 4, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(3000L, 500L, null, null, 1)),
                List.of());

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    @Test
    @DisplayName("scopeType=5 传入品类 refType：拒绝新建")
    void createRejectsMismatchedRefType() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                1, "商品满减", 1, null, null, 5, START, END, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(3000L, 500L, null, null, 1)),
                List.of(new PromotionSaveCommand.PromotionScopeRefCommand(3, 20L)));

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    @Test
    @DisplayName("结束时间不晚于开始时间：拒绝新建")
    void createRejectsInvalidSchedule() {
        PromotionSaveCommand command = new PromotionSaveCommand(
                1, "时间倒挂", 1, null, null, 1, END, START, 0, 100, null,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(3000L, 500L, null, null, 1)),
                List.of());

        assertBizCode(() -> promotionAdminService.create(command), ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).insert(any(Promotion.class));
    }

    // ---------------------------------------------------------------
    // 编辑与状态
    // ---------------------------------------------------------------

    @Test
    @DisplayName("编辑：先按 promotion_id 物理删除两张子表，再整批重插")
    void updateReplacesChildRows() {
        when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(existing());
        when(promotionMapper.update(isNull(), any())).thenReturn(1);

        promotionAdminService.update(PROMOTION_ID, ladderCommand());

        InOrder inOrder = inOrder(promotionRuleMapper, promotionScopeMapper);
        inOrder.verify(promotionRuleMapper).deleteByPromotionId(PROMOTION_ID);
        inOrder.verify(promotionScopeMapper).deleteByPromotionId(PROMOTION_ID);
        inOrder.verify(promotionRuleMapper, times(2)).insert(any(PromotionRule.class));
        inOrder.verify(promotionScopeMapper).insert(any(PromotionScope.class));
    }

    @Test
    @DisplayName("编辑不存在的活动：返回 404 资源不存在，不写子表")
    void updateMissingPromotion() {
        when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(null);

        assertBizCode(() -> promotionAdminService.update(PROMOTION_ID, ladderCommand()),
                ErrorCode.RESOURCE_NOT_FOUND);
        verify(promotionRuleMapper, never()).deleteByPromotionId(any());
        verify(promotionScopeMapper, never()).deleteByPromotionId(any());
    }

    @Test
    @DisplayName("状态变更：非法值报 PARAM_INVALID，既不查库也不更新")
    void changeStatusRejectsUnknownValue() {
        assertBizCode(() -> promotionAdminService.changeStatus(PROMOTION_ID, 4),
                ErrorCode.PARAM_INVALID);
        verify(promotionMapper, never()).selectById(any());
        verify(promotionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("状态变更：合法值 3 暂停时按主键条件更新")
    void changeStatusWritesConditionally() {
        when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(existing());
        when(promotionMapper.update(isNull(), any())).thenReturn(1);

        promotionAdminService.changeStatus(PROMOTION_ID, 3);

        verify(promotionMapper).update(isNull(), any());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private PromotionSaveCommand ladderCommand() {
        return new PromotionSaveCommand(
                1, "  阶梯满减  ", 1, null, null, 4, START, END, 1, 200, 100000L,
                List.of(new PromotionSaveCommand.PromotionRuleCommand(10000L, 1000L, null, null, 1),
                        new PromotionSaveCommand.PromotionRuleCommand(20000L, 3000L, null, null, 2)),
                List.of(new PromotionSaveCommand.PromotionScopeRefCommand(3, 20L)));
    }

    private Promotion existing() {
        Promotion promotion = new Promotion();
        promotion.setId(PROMOTION_ID);
        promotion.setPromotionType(1);
        promotion.setPromotionName("阶梯满减");
        promotion.setBearer(1);
        promotion.setScopeType(1);
        promotion.setStartAt(START);
        promotion.setEndAt(END);
        promotion.setStackable(0);
        promotion.setPriority(100);
        promotion.setStatus(1);
        promotion.setUsedBudget(0L);
        promotion.setDeleted(0);
        return promotion;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
