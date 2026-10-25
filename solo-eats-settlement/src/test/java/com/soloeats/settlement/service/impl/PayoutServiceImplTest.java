package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.domain.enums.PayoutStatus;
import com.soloeats.settlement.mapper.PayoutOrderMapper;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    private static final long MERCHANT_ID = 10L;
    private static final long RIDER_ID = 20L;

    @Mock
    private PayoutOrderMapper payoutOrderMapper;

    @InjectMocks
    private PayoutServiceImpl payoutService;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        // 纯单测不启动 Spring，LambdaUpdateWrapper 需要显式注册实体列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayoutOrder.class);
    }

    @Test
    @DisplayName("商家余额充足：申请成功，状态为待审核 0，单号与渠道落库")
    void merchantApplyWithinBalanceSucceeds() {
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
}
