package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberUser;
import com.soloeats.member.domain.entity.MemberWallet;
import com.soloeats.member.mapper.MemberAddressMapper;
import com.soloeats.member.mapper.MemberTagMapper;
import com.soloeats.member.mapper.MemberUserMapper;
import com.soloeats.member.mapper.MemberWalletMapper;
import com.soloeats.member.service.query.MemberQuery;
import com.soloeats.member.service.view.MemberDetailView;
import com.soloeats.member.support.PhoneCipher;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAdminServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private MemberUserMapper memberUserMapper;

    @Mock
    private MemberAddressMapper memberAddressMapper;

    @Mock
    private MemberWalletMapper memberWalletMapper;

    @Mock
    private MemberTagMapper memberTagMapper;

    private MemberAdminServiceImpl memberAdminService;

    @BeforeEach
    void setUp() {
        memberAdminService = new MemberAdminServiceImpl(
                memberUserMapper,
                memberAddressMapper,
                memberWalletMapper,
                memberTagMapper,
                new PhoneCipher("unit-test-phone-aes-key", "unit-test-phone-salt"));
    }

    @Test
    @DisplayName("列表按 status 过滤")
    void pageFiltersByStatus() {
        when(memberUserMapper.selectAdminPage(any(), eq(1), isNull(), isNull()))
                .thenReturn(pageWith(user()));

        memberAdminService.page(new MemberQuery(1, null, null), 1, 20);

        verify(memberUserMapper).selectAdminPage(any(), eq(1), isNull(), isNull());
    }

    @Test
    @DisplayName("keyword 只取手机号后四位，完整手机号不进入查询参数")
    void keywordUsesOnlyLastFourDigits() {
        when(memberUserMapper.selectAdminPage(any(), isNull(), eq("1234"), isNull()))
                .thenReturn(pageWith(user()));

        memberAdminService.page(new MemberQuery(null, "13800001234", null), 1, 20);

        verify(memberUserMapper).selectAdminPage(any(), isNull(), eq("1234"), isNull());
    }

    @Test
    @DisplayName("详情返回地址数、钱包余额与标签名，手机号只出现掩码")
    void detailReturnsAddressCountWalletBalanceAndTagNames() {
        when(memberUserMapper.selectById(USER_ID)).thenReturn(user());
        when(memberAddressMapper.selectCount(any())).thenReturn(3L);

        MemberWallet wallet = new MemberWallet();
        wallet.setUserId(USER_ID);
        wallet.setBalance(12_345L);
        when(memberWalletMapper.selectOne(any())).thenReturn(wallet);
        when(memberTagMapper.findTagNamesByUserId(USER_ID)).thenReturn(List.of("高频用户", "新客"));

        MemberDetailView view = memberAdminService.detail(USER_ID);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.addressCount()).isEqualTo(3L);
        assertThat(view.walletBalance()).isEqualTo(12_345L);
        assertThat(view.tagNames()).containsExactly("高频用户", "新客");
        assertThat(view.phone()).isEqualTo("138****8000");
        assertThat(view.phone()).doesNotContain("13800008000");
    }

    @Test
    @DisplayName("updateStatus 非法值抛 10001 且不落库")
    void updateStatusRejectsIllegalValue() {
        assertBizCode(() -> memberAdminService.updateStatus(USER_ID, 3),
                ErrorCode.PARAM_INVALID);
        assertBizCode(() -> memberAdminService.updateStatus(USER_ID, null),
                ErrorCode.PARAM_INVALID);

        verify(memberUserMapper, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("updateStatus 用户不存在抛 80001")
    void updateStatusMissingUserThrows80001() {
        when(memberUserMapper.updateStatus(USER_ID, 0)).thenReturn(0);

        assertBizCode(() -> memberAdminService.updateStatus(USER_ID, 0),
                ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("updateStatus 条件更新命中时正常返回")
    void updateStatusWritesWhenRowMatched() {
        when(memberUserMapper.updateStatus(USER_ID, 2)).thenReturn(1);

        memberAdminService.updateStatus(USER_ID, 2);

        verify(memberUserMapper).updateStatus(USER_ID, 2);
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }

    private static Page<MemberUser> pageWith(MemberUser... users) {
        Page<MemberUser> page = new Page<>(1, 20);
        page.setRecords(List.of(users));
        page.setTotal(users.length);
        return page;
    }

    private static MemberUser user() {
        MemberUser user = new MemberUser();
        user.setId(USER_ID);
        user.setPhoneMask("138****8000");
        user.setNickname("演示用户");
        user.setStatus(0);
        user.setOrderCount(0);
        user.setTotalAmount(0L);
        return user;
    }
}
