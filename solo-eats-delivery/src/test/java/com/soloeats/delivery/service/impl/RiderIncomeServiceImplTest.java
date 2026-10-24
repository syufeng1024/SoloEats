package com.soloeats.delivery.service.impl;

import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.RiderFeeRule;
import com.soloeats.delivery.domain.entity.RiderIncome;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.FreightRuleMapper;
import com.soloeats.delivery.mapper.RiderFeeRuleMapper;
import com.soloeats.delivery.mapper.RiderIncomeMapper;
import com.soloeats.delivery.mapper.RiderMapper;
import com.soloeats.delivery.mapper.ShopFreightRelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderIncomeServiceImplTest {

    private static final long DELIVERY_ID = 1001L;
    private static final long ORDER_ID = 9001L;
    private static final long RIDER_ID = 7L;
    private static final long SHOP_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now();
    /** 非高峰时间，保证补贴项为 0，断言不随运行时刻漂移。 */
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 10, 24, 10, 0);

    @Mock
    private RiderIncomeMapper riderIncomeMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private RiderFeeRuleMapper riderFeeRuleMapper;
    @Mock
    private FreightRuleMapper freightRuleMapper;
    @Mock
    private ShopFreightRelMapper shopFreightRelMapper;
    @Mock
    private RiderMapper riderMapper;

    @InjectMocks
    private RiderIncomeServiceImpl riderIncomeService;

    @Test
    @DisplayName("按骑手专属费率计算：基础 4 元 + 2.34km 按 3km 计 3 元")
    void settleUsesRiderSpecificRule() {
        stubDelivery();
        when(riderFeeRuleMapper.findActiveByRider(RIDER_ID, TODAY))
                .thenReturn(feeRule(RIDER_ID));
        stubFreightRules();
        when(riderIncomeMapper.selectOne(any())).thenReturn(null);

        riderIncomeService.settle(DELIVERY_ID);

        ArgumentCaptor<RiderIncome> captor = ArgumentCaptor.forClass(RiderIncome.class);
        verify(riderIncomeMapper).insert(captor.capture());
        RiderIncome saved = captor.getValue();
        assertThat(saved.getRiderId()).isEqualTo(RIDER_ID);
        assertThat(saved.getDeliveryOrderId()).isEqualTo(DELIVERY_ID);
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getBaseFee()).isEqualTo(400L);
        assertThat(saved.getDistanceFee()).isEqualTo(300L);
        assertThat(saved.getTimeSubsidy()).isZero();
        assertThat(saved.getWeatherSubsidy()).isZero();
        assertThat(saved.getTotalAmount()).isEqualTo(700L);
        assertThat(saved.getSettlementDate()).isEqualTo(TODAY);
        assertThat(saved.getStatus()).isZero();
        verify(riderFeeRuleMapper, never()).findActiveGlobal(any());
    }

    @Test
    @DisplayName("无骑手专属费率时使用全局费率")
    void settleFallsBackToGlobalRule() {
        stubDelivery();
        when(riderFeeRuleMapper.findActiveByRider(RIDER_ID, TODAY)).thenReturn(null);
        when(riderFeeRuleMapper.findActiveGlobal(TODAY)).thenReturn(feeRule(null));
        stubFreightRules();
        when(riderIncomeMapper.selectOne(any())).thenReturn(null);

        riderIncomeService.settle(DELIVERY_ID);

        ArgumentCaptor<RiderIncome> captor = ArgumentCaptor.forClass(RiderIncome.class);
        verify(riderIncomeMapper).insert(captor.capture());
        assertThat(captor.getValue().getBaseFee()).isEqualTo(400L);
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(700L);
        verify(riderFeeRuleMapper).findActiveGlobal(TODAY);
    }

    @Test
    @DisplayName("重复结算命中已有行时原地更新，不再插入")
    void settleTwiceUpdatesExistingRow() {
        stubDelivery();
        when(riderFeeRuleMapper.findActiveByRider(RIDER_ID, TODAY))
                .thenReturn(feeRule(RIDER_ID));
        stubFreightRules();
        RiderIncome existing = new RiderIncome();
        existing.setId(55L);
        existing.setRiderId(RIDER_ID);
        existing.setDeliveryOrderId(DELIVERY_ID);
        existing.setOrderId(ORDER_ID);
        existing.setTipAmount(50L);
        existing.setPenaltyAmount(20L);
        existing.setCompensationAmount(0L);
        existing.setSettlementDate(TODAY);
        existing.setStatus(0);
        when(riderIncomeMapper.selectOne(any())).thenReturn(existing);

        riderIncomeService.settle(DELIVERY_ID);

        verify(riderIncomeMapper, never()).insert(any(RiderIncome.class));
        verify(riderIncomeMapper).updateById(existing);
        // 400 + 300 + 小费 50 - 罚款 20
        assertThat(existing.getTotalAmount()).isEqualTo(730L);
        assertThat(existing.getBaseFee()).isEqualTo(400L);
        assertThat(existing.getDistanceFee()).isEqualTo(300L);
        assertThat(existing.getTipAmount()).isEqualTo(50L);
        assertThat(existing.getPenaltyAmount()).isEqualTo(20L);
    }

    private void stubDelivery() {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(DELIVERY_ID);
        delivery.setOrderId(ORDER_ID);
        delivery.setRiderId(RIDER_ID);
        delivery.setShopId(SHOP_ID);
        delivery.setDistanceM(2340);
        delivery.setDeliveredAt(DELIVERED_AT);
        when(deliveryOrderMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
    }

    private void stubFreightRules() {
        when(shopFreightRelMapper.findByShopId(SHOP_ID)).thenReturn(null);
        when(freightRuleMapper.selectList(any())).thenReturn(List.of());
    }

    private static RiderFeeRule feeRule(Long riderId) {
        RiderFeeRule rule = new RiderFeeRule();
        rule.setId(1L);
        rule.setRiderId(riderId);
        rule.setBaseFee(400L);
        rule.setPerKmFee(100L);
        rule.setPeakDelta(80L);
        rule.setBadWeatherDelta(150L);
        rule.setTimeoutPenalty(200L);
        rule.setStatus(0);
        return rule;
    }
}
