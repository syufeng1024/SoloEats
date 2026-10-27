package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.settlement.domain.entity.FundFlow;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.domain.enums.FlowType;
import com.soloeats.settlement.domain.enums.PayoutStatus;
import com.soloeats.settlement.mapper.FundFlowMapper;
import com.soloeats.settlement.mapper.PayoutOrderMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    private static final long MERCHANT_ID = 10L;
    private static final long RIDER_ID = 20L;

    @Mock
    private PayoutOrderMapper payoutOrderMapper;
    @Mock
    private DistributedLock distributedLock;
    @Mock
    private FundFlowMapper fundFlowMapper;

    @InjectMocks
    private PayoutServiceImpl payoutService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，LambdaUpdateWrapper 需要显式注册实体列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayoutOrder.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 资金流水用真实实现 + mock Mapper，验证到账确实写 t_fund_flow
        ReflectionTestUtils.setField(payoutService, "fundFlowService",
                new FundFlowServiceImpl(fundFlowMapper));
        // 分布式锁属于基础设施，直接 mock 并让锁体同步执行（不加锁等价于串行）
        lenient().when(distributedLock.executeWithLock(
                        anyString(), anyLong(), anyLong(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());
    }

    @Test
    @DisplayName("商家余额充足：申请成功，状态为待审核 0，单号与渠道落库")
    void merchantApplyWithinBalanceSucceeds() {
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(100_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID)).thenReturn(0L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            ((PayoutOrder) invocation.getArgument(0)).setId(501L);
            return 1;
        });

        PayoutView view = payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 30_000L, null);

        assertThat(view.id()).isEqualTo(501L);
        assertThat(view.status()).isEqualTo(PayoutStatus.PENDING.getCode());
        assertThat(view.amount()).isEqualTo(30_000L);
        assertThat(view.channel()).isEqualTo(1);
        assertThat(view.payoutNo()).startsWith("PO");

        ArgumentCaptor<PayoutOrder> captor = ArgumentCaptor.forClass(PayoutOrder.class);
        verify(payoutOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubjectType()).isEqualTo(1);
        assertThat(captor.getValue().getSubjectId()).isEqualTo(MERCHANT_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("超额申请：抛 60004，消息带当前可提现余额")
    void applyExceedingBalanceFails() {
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(1);
        when(payoutOrderMapper.sumMerchantSettledPayable(MERCHANT_ID)).thenReturn(5_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(1, MERCHANT_ID)).thenReturn(4_000L);

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 2_000L, 1))
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_AMOUNT_EXCEEDED.getCode());
                    assertThat(ex.getMessage()).contains("1000 分");
                });
    }

    @Test
    @DisplayName("待审核单重复审核：条件更新 0 行，抛 60005")
    void auditNonPendingPayoutFails() {
        when(payoutOrderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> payoutService.audit(1L, true, null, 1L))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.getCode()));
    }

    @Test
    @DisplayName("未审核直接打款：条件更新 0 行，抛 60005")
    void payWithoutApprovalFails() {
        when(payoutOrderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> payoutService.pay(1L))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.getCode()));
    }

    @Test
    @DisplayName("审核通过 → 打款 → 到账：三次条件更新状态依次为 10/20/30")
    void approvePayArriveTransitions() {
        when(payoutOrderMapper.update(isNull(), any())).thenReturn(1);

        payoutService.audit(1L, true, null, 1L);
        payoutService.pay(1L);
        payoutService.arrive(1L);

        ArgumentCaptor<LambdaUpdateWrapper<PayoutOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(payoutOrderMapper, times(3)).update(isNull(), captor.capture());
        List<LambdaUpdateWrapper<PayoutOrder>> wrappers = captor.getAllValues();
        assertThat(wrappers.get(0).getParamNameValuePairs().values())
                .contains(PayoutStatus.APPROVED.getCode());
        assertThat(wrappers.get(1).getParamNameValuePairs().values())
                .contains(PayoutStatus.PAYING.getCode());
        assertThat(wrappers.get(2).getParamNameValuePairs().values())
                .contains(PayoutStatus.ARRIVED.getCode());
    }

    @Test
    @DisplayName("驳回：状态置 40 并落 audit_remark")
    void rejectWritesAuditRemark() {
        when(payoutOrderMapper.update(isNull(), any())).thenReturn(1);

        payoutService.audit(9L, false, "收款账户信息不完整", 7L);

        ArgumentCaptor<LambdaUpdateWrapper<PayoutOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(payoutOrderMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PayoutOrder> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSet()).contains("audit_remark");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayoutStatus.REJECTED.getCode())
                .contains("收款账户信息不完整")
                .contains(7L);
    }

    @Test
    @DisplayName("骑手余额 = 收入合计 − 未驳回提现合计")
    void riderAvailableBalanceSubtractsOccupiedAmount() {
        when(payoutOrderMapper.sumRiderIncome(RIDER_ID)).thenReturn(8_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(2, RIDER_ID)).thenReturn(3_000L);

        long available = payoutService.availableBalance(PayoutService.SUBJECT_TYPE_RIDER, RIDER_ID);

        assertThat(available).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("商家无生效结算账户：抛 60006，不落提现单")
    void merchantWithoutSettlementAccountFails() {
        when(payoutOrderMapper.countActiveSettlementAccount(MERCHANT_ID)).thenReturn(0);

        assertThatThrownBy(() -> payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, MERCHANT_ID, 1_000L, 1))
                .isInstanceOfSatisfying(BizException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYOUT_ACCOUNT_REQUIRED.getCode()));
        verify(payoutOrderMapper, never()).insert(any(PayoutOrder.class));
    }

    @Test
    @DisplayName("骑手提现不校验结算账户（骑手侧无账户表）")
    void riderApplySkipsSettlementAccountCheck() {
        when(payoutOrderMapper.sumRiderIncome(RIDER_ID)).thenReturn(10_000L);
        when(payoutOrderMapper.sumActivePayoutAmount(2, RIDER_ID)).thenReturn(0L);
        when(payoutOrderMapper.insert(any(PayoutOrder.class))).thenAnswer(invocation -> {
            ((PayoutOrder) invocation.getArgument(0)).setId(502L);
            return 1;
        });

        PayoutView view = payoutService.apply(
                PayoutService.SUBJECT_TYPE_RIDER, RIDER_ID, 5_000L, 2);

        assertThat(view.id()).isEqualTo(502L);
        assertThat(view.status()).isEqualTo(PayoutStatus.PENDING.getCode());
        verify(payoutOrderMapper, never()).countActiveSettlementAccount(any());
    }

    @Test
    @DisplayName("到账成功：写一条提现支出流水 flow_type=5 direction=2，金额取申请金额")
    void arriveWritesPayoutExpenseFundFlow() {
        when(payoutOrderMapper.update(isNull(), any())).thenReturn(1);
        PayoutOrder order = new PayoutOrder();
        order.setId(1L);
        order.setPayoutNo("PO20261027000001");
        order.setSubjectType(PayoutService.SUBJECT_TYPE_MERCHANT);
        order.setSubjectId(MERCHANT_ID);
        order.setAmount(30_000L);
        order.setStatus(PayoutStatus.ARRIVED.getCode());
        when(payoutOrderMapper.selectById(1L)).thenReturn(order);

        payoutService.arrive(1L);

        ArgumentCaptor<FundFlow> captor = ArgumentCaptor.forClass(FundFlow.class);
        verify(fundFlowMapper).insert(captor.capture());
        FundFlow flow = captor.getValue();
        assertThat(flow.getFlowType()).isEqualTo(FlowType.PAYOUT_EXPENSE.getCode());
        assertThat(flow.getDirection()).isEqualTo(FlowType.DIRECTION_OUT);
        assertThat(flow.getAmount()).isEqualTo(30_000L);
        assertThat(flow.getBizType()).isEqualTo(FundFlowService.BIZ_TYPE_PAYOUT_ORDER);
        assertThat(flow.getBizId()).isEqualTo(1L);
    }
}
