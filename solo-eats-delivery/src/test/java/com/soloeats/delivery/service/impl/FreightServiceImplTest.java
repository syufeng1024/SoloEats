package com.soloeats.delivery.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.FreightRule;
import com.soloeats.delivery.domain.entity.FreightTemplate;
import com.soloeats.delivery.mapper.FreightRuleMapper;
import com.soloeats.delivery.mapper.FreightTemplateMapper;
import com.soloeats.delivery.mapper.ShopFreightRelMapper;
import com.soloeats.delivery.service.command.FreightEstimateCommand;
import com.soloeats.delivery.service.view.FreightEstimateView;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.view.ShopDetailView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreightServiceImplTest {

    private static final long SHOP_ID = 1L;
    private static final BigDecimal SHOP_LNG = new BigDecimal("116.480000");
    private static final BigDecimal SHOP_LAT = new BigDecimal("39.990000");

    @Mock
    private FreightTemplateMapper freightTemplateMapper;
    @Mock
    private FreightRuleMapper freightRuleMapper;
    @Mock
    private ShopFreightRelMapper shopFreightRelMapper;
    @Mock
    private ShopService shopService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FreightServiceImpl freightService;

    @Test
    @DisplayName("到店自取直接返回 0，不查模板与门店")
    void pickupReturnsZero() {
        FreightEstimateView view = freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, null, null, 5000L, null, null, null, 2));

        assertThat(view.deliveryFee()).isZero();
        assertThat(view.distanceM()).isZero();
        assertThat(view.breakdown()).singleElement()
                .extracting(FreightEstimateView.Item::name)
                .isEqualTo("到店自取免配送费");
        verifyNoInteractions(freightTemplateMapper, freightRuleMapper,
                shopFreightRelMapper, shopService);
    }

    @Test
    @DisplayName("超出起步距离后每 500m 加 1 元")
    void distanceLadderChargesPerStep() {
        stubTemplateAndShop(null);

        // 纬度 +0.045 度 ≈ 5009m，超出 3000m 后按 5 个阶梯计费
        FreightEstimateView view = freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, SHOP_LNG, SHOP_LAT.add(new BigDecimal("0.045")),
                1000L, null, null, null, 1));

        assertThat(view.distanceM()).isBetween(5000, 5050);
        assertThat(itemAmount(view, "起步价")).isEqualTo(500L);
        assertThat(itemAmount(view, "距离费")).isEqualTo(500L);
        assertThat(view.deliveryFee()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("超出模板最大配送距离抛 50011")
    void outOfRangeRejected() {
        stubTemplateAndShop(null);

        // 纬度 +0.1 度 ≈ 11132m，超过 max_distance_m = 10000
        assertThatThrownBy(() -> freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, SHOP_LNG, SHOP_LAT.add(new BigDecimal("0.1")),
                1000L, null, null, null, 1)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.FREIGHT_OUT_OF_RANGE.getCode());
    }

    @Test
    @DisplayName("商品金额达到门槛时配送费置 0，减免项为负数保证 breakdown 求和一致")
    void freeOverAmountDeductsFee() {
        stubTemplateAndShop(null);

        FreightEstimateView view = freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, SHOP_LNG, SHOP_LAT, 3000L, null, null, null, 1));

        assertThat(view.deliveryFee()).isZero();
        assertThat(itemAmount(view, "满额减免")).isEqualTo(-500L);
        assertThat(view.breakdown().stream()
                .mapToLong(FreightEstimateView.Item::amount).sum()).isZero();
    }

    @Test
    @DisplayName("送达时间落在高峰窗口时加收时段费")
    void peakTimeAddsSurcharge() {
        stubTemplateAndShop(List.of(timeRule(
                "{\"windows\":[{\"start\":\"11:00\",\"end\":\"13:00\"}]}", 100L)));

        FreightEstimateView view = freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, SHOP_LNG, SHOP_LAT, 1000L, null, null,
                LocalDateTime.of(2026, 10, 24, 12, 0), 1));

        assertThat(itemAmount(view, "时段加价")).isEqualTo(100L);
        assertThat(view.deliveryFee()).isEqualTo(600L);
    }

    @Test
    @DisplayName("门店未绑定模板时回落 id=1 默认模板")
    void unboundShopFallsBackToDefaultTemplate() {
        stubTemplateAndShop(null);

        FreightEstimateView view = freightService.estimate(new FreightEstimateCommand(
                SHOP_ID, SHOP_LNG, SHOP_LAT, 1000L, null, null, null, 1));

        assertThat(itemAmount(view, "起步价")).isEqualTo(500L);
        assertThat(view.deliveryFee()).isEqualTo(500L);
        verify(freightTemplateMapper).selectById(1L);
    }

    private void stubTemplateAndShop(List<FreightRule> rules) {
        ShopDetailView shop = mock(ShopDetailView.class);
        when(shop.lng()).thenReturn(SHOP_LNG);
        when(shop.lat()).thenReturn(SHOP_LAT);
        when(shopService.detail(SHOP_ID)).thenReturn(shop);
        when(shopFreightRelMapper.findByShopId(SHOP_ID)).thenReturn(null);
        when(freightTemplateMapper.selectById(1L)).thenReturn(defaultTemplate());
        when(freightRuleMapper.selectList(any())).thenReturn(rules == null ? List.of() : rules);
    }

    private static long itemAmount(FreightEstimateView view, String name) {
        return view.breakdown().stream()
                .filter(item -> name.equals(item.name()))
                .mapToLong(FreightEstimateView.Item::amount)
                .findFirst()
                .orElse(0L);
    }

    private static FreightTemplate defaultTemplate() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setTemplateName("默认运费模板");
        template.setBaseFee(500L);
        template.setBaseDistanceM(3000);
        template.setPerStepDistanceM(500);
        template.setPerStepFee(100L);
        template.setMaxDistanceM(10000);
        template.setFreeOverAmount(3000L);
        template.setStatus(0);
        return template;
    }

    private static FreightRule timeRule(String conditionJson, long feeDelta) {
        FreightRule rule = new FreightRule();
        rule.setId(1L);
        rule.setRuleType(FreightServiceImpl.RULE_TIME);
        rule.setConditionJson(conditionJson);
        rule.setFeeDelta(feeDelta);
        rule.setSort(1);
        rule.setStatus(0);
        return rule;
    }
}
