package com.soloeats.marketing.service.impl;

import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.service.MarketingCalculateCommand;
import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.marketing.service.view.MarketingCalculateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private PromotionServiceImpl promotionService;

    @Test
    @DisplayName("满 30 减 5：3500 分命中 3000 门槛，抵扣 500，实付 3000，分摊合计 500")
    void hitFullReduction() {
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT))
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
        when(promotionMapper.selectActivePlatformPromotions(AT)).thenReturn(List.of());

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

    private MarketingCalculateCommand command(long goodsAmount, long couponDiscount,
                                              List<MarketingCalculateCommand.MarketingItem> items) {
        return new MarketingCalculateCommand(
                1L, 1L, goodsAmount, items, couponDiscount, AT);
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

    private PromotionRule rule(long id, long thresholdAmount, long discountAmount, int sort) {
        PromotionRule rule = new PromotionRule();
        rule.setId(id);
        rule.setPromotionId(1L);
        rule.setThresholdAmount(thresholdAmount);
        rule.setDiscountAmount(discountAmount);
        rule.setSort(sort);
        return rule;
    }

    private long apportionSum(MarketingCalculateResult result) {
        return result.apportion().stream()
                .mapToLong(DiscountApportionItem::discountAmount)
                .sum();
    }
}
