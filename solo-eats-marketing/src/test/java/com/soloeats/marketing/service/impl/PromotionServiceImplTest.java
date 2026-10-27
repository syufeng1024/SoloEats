package com.soloeats.marketing.service.impl;

import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.mapper.PromotionScopeMapper;
import com.soloeats.marketing.service.MarketingCalculateCommand;
import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.marketing.service.view.MarketingCalculateResult;
import com.soloeats.marketing.service.view.PromotionAvailableView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceImplTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2030, 12, 31, 23, 59, 59);

    @Mock
    private PromotionMapper promotionMapper;

    @Mock
    private PromotionRuleMapper promotionRuleMapper;

    @Mock
    private PromotionScopeMapper promotionScopeMapper;

    @InjectMocks
    private PromotionServiceImpl promotionService;

    @Test
    @DisplayName("满 30 减 5：3500 分命中 3000 门槛，抵扣 500，实付 3000，分摊合计 500")
    void hitFullReduction() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, START, END)));
        when(promotionRuleMapper.selectBestRule(1L, 3500L))
                .thenReturn(rule(11L, 3000L, 500L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                3500L, 0L, List.of(item(1001L, 3000L), item(1002L, 500L))));

        assertThat(result.promotionId()).isEqualTo(1L);
        assertThat(result.promotionName()).isEqualTo("平台满30减5");
        assertThat(result.discountAmount()).isEqualTo(500L);
        assertThat(result.payableAmount()).isEqualTo(3000L);
        assertThat(result.apportion())
                .extracting(DiscountApportionItem::skuId, DiscountApportionItem::discountAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1001L, 429L),
                        org.assertj.core.groups.Tuple.tuple(1002L, 71L));
        assertThat(apportionSum(result)).isEqualTo(500L);
        assertThat(result.hitRules()).singleElement().satisfies(hit -> {
            assertThat(hit.ruleId()).isEqualTo(11L);
            assertThat(hit.thresholdAmount()).isEqualTo(3000L);
            assertThat(hit.discountAmount()).isEqualTo(500L);
            assertThat(hit.sort()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("未达门槛：goodsAmount=2000 小于 3000，不命中且不抛异常")
    void belowThreshold() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, START, END)));
        when(promotionRuleMapper.selectBestRule(1L, 2000L))
                .thenReturn(rule(11L, 3000L, 500L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                2000L, 0L, List.of(item(1001L, 2000L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payableAmount()).isEqualTo(2000L);
        assertThat(result.apportion()).isEmpty();
        assertThat(result.hitRules()).isEmpty();
    }

    @Test
    @DisplayName("活动未开始：at 早于 start_at，不命中")
    void notStarted() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, AT.plusMinutes(1), END)));

        MarketingCalculateResult result = promotionService.calculate(command(
                3500L, 0L, List.of(item(1001L, 3500L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payableAmount()).isEqualTo(3500L);
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("活动已结束：at 晚于 end_at，不命中")
    void alreadyEnded() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, START, AT.minusSeconds(1))));

        MarketingCalculateResult result = promotionService.calculate(command(
                3500L, 0L, List.of(item(1001L, 3500L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("券与满减互斥：stackable=0 且已有券抵扣时，满减不适用")
    void exclusiveWithCoupon() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 0, START, END)));

        MarketingCalculateResult result = promotionService.calculate(command(
                3500L, 500L, List.of(item(1001L, 3500L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payableAmount()).isEqualTo(3000L);
        assertThat(result.apportion()).isEmpty();
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("stackable=1 时允许与券叠加：券 500 + 满减 500，实付 2500")
    void stackableWithCoupon() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, START, END)));
        when(promotionRuleMapper.selectBestRule(1L, 3500L))
                .thenReturn(rule(11L, 3000L, 500L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                3500L, 500L, List.of(item(1001L, 3500L))));

        assertThat(result.promotionId()).isEqualTo(1L);
        assertThat(result.discountAmount()).isEqualTo(500L);
        assertThat(result.payableAmount()).isEqualTo(2500L);
        assertThat(apportionSum(result)).isEqualTo(500L);
    }

    @Test
    @DisplayName("实付保底 1 分：商品 100 分、满减 10000 分时实付仍为 1 分")
    void payableFloorAtOneCent() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "平台满30减5", 1, START, END)));
        when(promotionRuleMapper.selectBestRule(1L, 100L))
                .thenReturn(rule(11L, 0L, 10000L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                100L, 0L, List.of(item(1001L, 100L))));

        assertThat(result.promotionId()).isEqualTo(1L);
        assertThat(result.discountAmount()).isEqualTo(100L);
        assertThat(result.payableAmount()).isEqualTo(1L);
        assertThat(apportionSum(result)).isEqualTo(100L);
    }

    @Test
    @DisplayName("没有任何活动：返回 0 抵扣与券后实付，不抛异常")
    void noPromotionAtAll() {
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of());

        MarketingCalculateResult result = promotionService.calculate(command(
                2000L, 300L, List.of(item(1001L, 2000L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.promotionName()).isNull();
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payableAmount()).isEqualTo(1700L);
        assertThat(result.apportion()).isEmpty();
        assertThat(result.hitRules()).isEmpty();
        verifyNoInteractions(promotionRuleMapper);
    }

    // ---------------------------------------------------------------
    // 阶段A 新增：阶梯 / 商家范围 / 折扣 / 优先级 / 商品与品类范围
    // ---------------------------------------------------------------

    @Test
    @DisplayName("阶梯满减取达标的最大门槛档：25000 分命中满200减30，抵扣 3000")
    void picksBestTierForLadderReduction() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(promotion(1L, "阶梯满减", 1, START, END)));
        when(promotionRuleMapper.selectBestRule(1L, 25000L))
                .thenReturn(rule(12L, 20000L, 3000L, 2));

        MarketingCalculateResult result = promotionService.calculate(command(
                25000L, 0L, List.of(item(1001L, 25000L))));

        assertThat(result.promotionId()).isEqualTo(1L);
        assertThat(result.discountAmount()).isEqualTo(3000L);
        assertThat(result.hitRules()).singleElement()
                .satisfies(hit -> assertThat(hit.thresholdAmount()).isEqualTo(20000L));
        verify(promotionRuleMapper).selectBestRule(1L, 25000L);
    }

    @Test
    @DisplayName("商家满减：bearer=2、scopeType=2、merchantId 一致时命中")
    void merchantPromotionHitsWhenMerchantMatches() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(merchantPromotion(2L, "商家满50减8", 7L)));
        when(promotionRuleMapper.selectBestRule(2L, 5000L))
                .thenReturn(rule(21L, 5000L, 800L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                5000L, 0L, List.of(item(1001L, 5000L)), 7L));

        assertThat(result.promotionId()).isEqualTo(2L);
        assertThat(result.discountAmount()).isEqualTo(800L);
        assertThat(result.payableAmount()).isEqualTo(4200L);
    }

    @Test
    @DisplayName("商家满减：本次订单商家与活动 merchant_id 不一致时不命中")
    void merchantPromotionMissesWhenMerchantDiffers() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(merchantPromotion(2L, "商家满50减8", 7L)));

        MarketingCalculateResult result = promotionService.calculate(command(
                5000L, 0L, List.of(item(1001L, 5000L)), 8L));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("商家满减：C 端只传 shopId，服务端反查 merchant_id=7 后命中")
    void merchantPromotionHitsAfterResolvingMerchantByShop() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(merchantPromotion(2L, "商家满50减8", 7L)));
        when(promotionMapper.selectMerchantIdByShop(1L)).thenReturn(7L);
        when(promotionRuleMapper.selectBestRule(2L, 5000L))
                .thenReturn(rule(21L, 5000L, 800L, 1));

        // 6 参兼容构造：merchantId 为 null，只能靠 shopId 反查
        MarketingCalculateResult result = promotionService.calculate(command(
                5000L, 0L, List.of(item(1001L, 5000L))));

        assertThat(result.promotionId()).isEqualTo(2L);
        assertThat(result.discountAmount()).isEqualTo(800L);
        assertThat(result.payableAmount()).isEqualTo(4200L);
        verify(promotionMapper, times(1)).selectMerchantIdByShop(1L);
    }

    @Test
    @DisplayName("商家满减：shopId 反查不到商家时跳过，且只反查一次")
    void merchantPromotionSkippedWhenShopHasNoMerchant() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(merchantPromotion(2L, "商家满50减8", 7L)));
        when(promotionMapper.selectMerchantIdByShop(1L)).thenReturn(null);

        MarketingCalculateResult result = promotionService.calculate(command(
                5000L, 0L, List.of(item(1001L, 5000L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        assertThat(result.payableAmount()).isEqualTo(5000L);
        // 只解析一次商家归属，不在候选循环里逐条查库
        verify(promotionMapper, times(1)).selectMerchantIdByShop(1L);
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("折扣活动：8.5 折、商品 10000 分，抵扣 1500 分")
    void discountPromotionComputesFromRate() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(discountPromotion(3L, "8.5折")));
        when(promotionRuleMapper.selectBestRule(3L, 10000L))
                .thenReturn(discountRule(31L, 0L, "0.85", null, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                10000L, 0L, List.of(item(1001L, 10000L))));

        assertThat(result.promotionId()).isEqualTo(3L);
        assertThat(result.discountAmount()).isEqualTo(1500L);
        assertThat(result.payableAmount()).isEqualTo(8500L);
    }

    @Test
    @DisplayName("折扣活动封顶：算出 1500 分但 maxDiscount=1000，只抵扣 1000 分")
    void discountPromotionCappedByMaxDiscount() {
        when(promotionMapper.selectCandidates(AT))
                .thenReturn(List.of(discountPromotion(3L, "8.5折封顶10元")));
        when(promotionRuleMapper.selectBestRule(3L, 10000L))
                .thenReturn(discountRule(31L, 0L, "0.85", 1000L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                10000L, 0L, List.of(item(1001L, 10000L))));

        assertThat(result.discountAmount()).isEqualTo(1000L);
        assertThat(result.payableAmount()).isEqualTo(9000L);
    }

    @Test
    @DisplayName("优先级：priority 9 与 5 同时可用时取 9，且不再为 5 取档位")
    void takesHighestPriorityCandidate() {
        Promotion high = promotion(9L, "高优先级", 1, START, END);
        high.setPriority(9);
        Promotion low = promotion(5L, "低优先级", 1, START, END);
        low.setPriority(5);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(high, low));
        when(promotionRuleMapper.selectBestRule(9L, 3000L))
                .thenReturn(rule(91L, 3000L, 300L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                3000L, 0L, List.of(item(1001L, 3000L))));

        assertThat(result.promotionId()).isEqualTo(9L);
        assertThat(result.discountAmount()).isEqualTo(300L);
        verify(promotionRuleMapper, times(1)).selectBestRule(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(promotionRuleMapper, never()).selectBestRule(5L, 3000L);
    }

    @Test
    @DisplayName("指定商品范围：订单 sku 与活动范围有交集时命中")
    void scopeGoodsHitsWhenSkuInScope() {
        Promotion promotion = promotion(4L, "指定商品满减", 1, START, END);
        promotion.setScopeType(5);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(promotion));
        when(promotionScopeMapper.selectScopeSkuIds(4L)).thenReturn(List.of(1001L, 1003L));
        when(promotionRuleMapper.selectBestRule(4L, 4000L))
                .thenReturn(rule(41L, 3000L, 400L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                4000L, 0L, List.of(item(1001L, 4000L))));

        assertThat(result.promotionId()).isEqualTo(4L);
        assertThat(result.discountAmount()).isEqualTo(400L);
    }

    @Test
    @DisplayName("指定商品范围：订单 sku 不在范围内时不命中")
    void scopeGoodsMissesWhenSkuOutOfScope() {
        Promotion promotion = promotion(4L, "指定商品满减", 1, START, END);
        promotion.setScopeType(5);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(promotion));
        when(promotionScopeMapper.selectScopeSkuIds(4L)).thenReturn(List.of(9999L));

        MarketingCalculateResult result = promotionService.calculate(command(
                4000L, 0L, List.of(item(1001L, 4000L))));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        verifyNoInteractions(promotionRuleMapper);
    }

    @Test
    @DisplayName("指定品类范围：订单 sku 所属品类在活动品类内时命中")
    void scopeCategoryHitsWhenOrderCategoryInScope() {
        Promotion promotion = promotion(5L, "品类满减", 1, START, END);
        promotion.setScopeType(4);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(promotion));
        when(promotionScopeMapper.selectCategoryIdsBySkuIds(List.of(1001L)))
                .thenReturn(List.of(20L));
        when(promotionScopeMapper.selectScopeCategoryIds(5L)).thenReturn(List.of(20L, 21L));
        when(promotionRuleMapper.selectBestRule(5L, 3000L))
                .thenReturn(rule(51L, 3000L, 300L, 1));

        MarketingCalculateResult result = promotionService.calculate(command(
                3000L, 0L, List.of(item(1001L, 3000L))));

        assertThat(result.promotionId()).isEqualTo(5L);
        assertThat(result.discountAmount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("指定品类范围：无商品明细时无法判定，直接跳过且不反查品类")
    void scopeCategorySkippedWithoutItems() {
        Promotion promotion = promotion(5L, "品类满减", 1, START, END);
        promotion.setScopeType(4);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(promotion));

        MarketingCalculateResult result = promotionService.calculate(command(
                3000L, 0L, List.of()));

        assertThat(result.promotionId()).isNull();
        assertThat(result.discountAmount()).isZero();
        verifyNoInteractions(promotionScopeMapper, promotionRuleMapper);
    }

    // ---------------------------------------------------------------
    // C 端可见活动
    // ---------------------------------------------------------------

    @Test
    @DisplayName("C 端可见活动：全平台活动命中并带出档位，未命中门店的活动被剔除")
    void listAvailableFiltersByScope() {
        Promotion allPlatform = promotion(6L, "平台满30减5", 1, START, END);
        Promotion otherShop = promotion(7L, "其他门店满减", 1, START, END);
        otherShop.setScopeType(3);
        otherShop.setShopId(99L);
        when(promotionMapper.selectCandidates(AT)).thenReturn(List.of(allPlatform, otherShop));
        when(promotionRuleMapper.selectByPromotionId(6L))
                .thenReturn(List.of(rule(61L, 3000L, 500L, 1)));

        List<PromotionAvailableView> views = promotionService.listAvailable(1L, List.of(), AT);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(6L);
            assertThat(view.promotionName()).isEqualTo("平台满30减5");
            assertThat(view.rules()).singleElement()
                    .satisfies(rule -> assertThat(rule.thresholdAmount()).isEqualTo(3000L));
        });
        verifyNoInteractions(promotionScopeMapper);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private MarketingCalculateCommand command(long goodsAmount, long couponDiscount,
                                              List<MarketingCalculateCommand.MarketingItem> items) {
        // 走 6 参兼容构造，同时验证阶段A-0 调用方无需改动
        return new MarketingCalculateCommand(
                1L, 1L, goodsAmount, items, couponDiscount, AT);
    }

    private MarketingCalculateCommand command(long goodsAmount, long couponDiscount,
                                              List<MarketingCalculateCommand.MarketingItem> items,
                                              long merchantId) {
        return new MarketingCalculateCommand(
                1L, 1L, goodsAmount, items, couponDiscount, AT, merchantId);
    }

    private MarketingCalculateCommand.MarketingItem item(long skuId, long payableAmount) {
        return new MarketingCalculateCommand.MarketingItem(skuId, 1, payableAmount);
    }

    private Promotion promotion(long id, String name, int stackable,
                                LocalDateTime startAt, LocalDateTime endAt) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setPromotionType(1);
        promotion.setPromotionName(name);
        promotion.setBearer(1);
        promotion.setScopeType(1);
        promotion.setStartAt(startAt);
        promotion.setEndAt(endAt);
        promotion.setStackable(stackable);
        promotion.setPriority(100);
        promotion.setStatus(1);
        promotion.setDeleted(0);
        return promotion;
    }

    private Promotion merchantPromotion(long id, String name, long merchantId) {
        Promotion promotion = promotion(id, name, 1, START, END);
        promotion.setBearer(2);
        promotion.setScopeType(2);
        promotion.setMerchantId(merchantId);
        return promotion;
    }

    private Promotion discountPromotion(long id, String name) {
        Promotion promotion = promotion(id, name, 1, START, END);
        promotion.setPromotionType(2);
        return promotion;
    }

    private PromotionRule rule(long id, long thresholdAmount, long discountAmount, int sort) {
        PromotionRule rule = new PromotionRule();
        rule.setId(id);
        rule.setPromotionId(1L);
        rule.setThresholdAmount(thresholdAmount);
        rule.setDiscountAmount(discountAmount);
        rule.setSort(sort);
        return rule;
    }

    private PromotionRule discountRule(long id, long thresholdAmount, String rate,
                                       Long maxDiscount, int sort) {
        PromotionRule rule = new PromotionRule();
        rule.setId(id);
        rule.setPromotionId(1L);
        rule.setThresholdAmount(thresholdAmount);
        rule.setDiscountRate(new BigDecimal(rate));
        rule.setMaxDiscount(maxDiscount);
        rule.setSort(sort);
        return rule;
    }

    private long apportionSum(MarketingCalculateResult result) {
        return result.apportion().stream()
                .mapToLong(DiscountApportionItem::discountAmount)
                .sum();
    }
}
