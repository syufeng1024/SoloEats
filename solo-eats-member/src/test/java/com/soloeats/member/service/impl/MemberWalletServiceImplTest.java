package com.soloeats.member.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberWallet;
import com.soloeats.member.domain.entity.MemberWalletLog;
import com.soloeats.member.domain.enums.WalletBizType;
import com.soloeats.member.mapper.MemberWalletLogMapper;
import com.soloeats.member.mapper.MemberWalletMapper;
import com.soloeats.member.service.view.WalletView;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberWalletServiceImplTest {

    private static final long USER_ID = 9L;
    private static final String REQUEST_ID = "rc-001";

    @Mock
    private MemberWalletMapper memberWalletMapper;

    @Mock
    private MemberWalletLogMapper memberWalletLogMapper;

    private MemberWalletServiceImpl memberWalletService;

    @BeforeEach
    void setUp() {
        memberWalletService = new MemberWalletServiceImpl(
                memberWalletMapper, memberWalletLogMapper, true);
    }

    @Test
    @DisplayName("充值成功：余额增加，流水 before/after 正确")
    void rechargeWritesBalanceAndLog() {
        when(memberWalletLogMapper.findByRequestId("RECHARGE", USER_ID, REQUEST_ID)).thenReturn(null);
        when(memberWalletMapper.selectOne(any())).thenReturn(wallet(1000L, 0), wallet(1500L, 1));
        when(memberWalletMapper.credit(USER_ID, 500L, 0)).thenReturn(1);
        when(memberWalletMapper.addTotalRecharge(USER_ID, 500L)).thenReturn(1);

        WalletView view = memberWalletService.recharge(USER_ID, 500L, REQUEST_ID);

        assertThat(view.balance()).isEqualTo(1500L);
        ArgumentCaptor<MemberWalletLog> captor = ArgumentCaptor.forClass(MemberWalletLog.class);
        verify(memberWalletLogMapper).insert(captor.capture());
        MemberWalletLog log = captor.getValue();
        assertThat(log.getBizType()).isEqualTo("RECHARGE");
        assertThat(log.getBizId()).isEqualTo(USER_ID);
        assertThat(log.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(log.getDirection()).isEqualTo(1);
        assertThat(log.getAmount()).isEqualTo(500L);
        assertThat(log.getBeforeBalance()).isEqualTo(1000L);
        assertThat(log.getAfterBalance()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("同 requestId 重复充值不重复入账")
    void rechargeIsIdempotentByRequestId() {
        MemberWalletLog existing = new MemberWalletLog();
        existing.setBizType("RECHARGE");
        existing.setBizId(USER_ID);
        existing.setRequestId(REQUEST_ID);
        when(memberWalletLogMapper.findByRequestId("RECHARGE", USER_ID, REQUEST_ID))
                .thenReturn(existing);
        when(memberWalletMapper.selectOne(any())).thenReturn(wallet(1500L, 1));

        WalletView view = memberWalletService.recharge(USER_ID, 500L, REQUEST_ID);

        assertThat(view.balance()).isEqualTo(1500L);
        verify(memberWalletMapper, never()).credit(any(), any(), any());
        verify(memberWalletMapper, never()).addTotalRecharge(any(), any());
        verify(memberWalletLogMapper, never()).insert(any(MemberWalletLog.class));
    }

    @Test
    @DisplayName("余额不足抛 80006 且不动余额")
    void deductRejectedWhenBalanceNotEnough() {
        when(memberWalletLogMapper.findByRequestId("CONSUME", 77L, "od-1")).thenReturn(null);
        when(memberWalletMapper.selectOne(any())).thenReturn(wallet(100L, 0));

        assertBizCode(() -> memberWalletService.deduct(
                        USER_ID, 500L, WalletBizType.CONSUME, 77L, "od-1"),
                ErrorCode.WALLET_BALANCE_NOT_ENOUGH);

        verify(memberWalletMapper, never()).deduct(any(), any(), any());
        verify(memberWalletLogMapper, never()).insert(any(MemberWalletLog.class));
    }

    @Test
    @DisplayName("扣减影响行数为 0 时重查 version 重试后成功")
    void deductRetriesOnVersionConflict() {
        when(memberWalletLogMapper.findByRequestId("CONSUME", 77L, "od-1")).thenReturn(null);
        when(memberWalletMapper.selectOne(any()))
                .thenReturn(wallet(500L, 0), wallet(500L, 1), wallet(400L, 2));
        when(memberWalletMapper.deduct(USER_ID, 100L, 0)).thenReturn(0);
        when(memberWalletMapper.deduct(USER_ID, 100L, 1)).thenReturn(1);
        when(memberWalletMapper.addTotalConsume(USER_ID, 100L)).thenReturn(1);

        memberWalletService.deduct(USER_ID, 100L, WalletBizType.CONSUME, 77L, "od-1");

        verify(memberWalletMapper, times(2)).deduct(eq(USER_ID), eq(100L), any());
        ArgumentCaptor<MemberWalletLog> captor = ArgumentCaptor.forClass(MemberWalletLog.class);
        verify(memberWalletLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getDirection()).isEqualTo(2);
        assertThat(captor.getValue().getBeforeBalance()).isEqualTo(500L);
        assertThat(captor.getValue().getAfterBalance()).isEqualTo(400L);
    }

    @Test
    @DisplayName("调账负数扣减成功，流水记 ADJUST 与操作人+原因")
    void adjustNegativeWritesLog() {
        when(memberWalletLogMapper.findByRequestId("ADJUST", USER_ID, "adj-1")).thenReturn(null);
        when(memberWalletMapper.selectOne(any())).thenReturn(wallet(1000L, 0), wallet(700L, 1));
        when(memberWalletMapper.deduct(USER_ID, 300L, 0)).thenReturn(1);

        WalletView view = memberWalletService.adjust(
                USER_ID, -300L, "多充退回", "adj-1", 1L, "admin");

        assertThat(view.balance()).isEqualTo(700L);
        verify(memberWalletMapper, never()).addTotalConsume(any(), any());
        ArgumentCaptor<MemberWalletLog> captor = ArgumentCaptor.forClass(MemberWalletLog.class);
        verify(memberWalletLogMapper).insert(captor.capture());
        MemberWalletLog log = captor.getValue();
        assertThat(log.getBizType()).isEqualTo("ADJUST");
        assertThat(log.getDirection()).isEqualTo(2);
        assertThat(log.getAmount()).isEqualTo(300L);
        assertThat(log.getRemark()).isEqualTo("admin：多充退回");
        assertThat(log.getOperatorId()).isEqualTo(1L);
        assertThat(log.getOperatorName()).isEqualTo("admin");
    }

    @Test
    @DisplayName("充值金额超上限抛 80007，且不触碰钱包")
    void rechargeAmountOverLimitRejected() {
        assertBizCode(() -> memberWalletService.recharge(USER_ID, 100_001L, REQUEST_ID),
                ErrorCode.WALLET_AMOUNT_INVALID);

        verifyNoInteractions(memberWalletMapper, memberWalletLogMapper);
    }

    @Test
    @DisplayName("模拟充值开关关闭时拒绝充值")
    void rechargeRejectedWhenMockDisabled() {
        MemberWalletServiceImpl disabled = new MemberWalletServiceImpl(
                memberWalletMapper, memberWalletLogMapper, false);

        assertBizCode(() -> disabled.recharge(USER_ID, 500L, REQUEST_ID),
                ErrorCode.PARAM_INVALID);

        verifyNoInteractions(memberWalletMapper, memberWalletLogMapper);
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }

    private MemberWallet wallet(long balance, int version) {
        MemberWallet wallet = new MemberWallet();
        wallet.setUserId(USER_ID);
        wallet.setBalance(balance);
        wallet.setFrozenAmount(0L);
        wallet.setTotalRecharge(0L);
        wallet.setTotalConsume(0L);
        wallet.setVersion(version);
        return wallet;
    }
}
