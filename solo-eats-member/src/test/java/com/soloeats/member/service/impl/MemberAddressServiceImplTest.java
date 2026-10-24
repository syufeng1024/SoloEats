package com.soloeats.member.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberAddress;
import com.soloeats.member.mapper.MemberAddressMapper;
import com.soloeats.member.service.command.SaveAddressCommand;
import com.soloeats.member.service.view.AddressView;
import com.soloeats.member.support.PhoneCipher;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAddressServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long ADDRESS_ID = 100L;
    private static final long PROMOTED_ADDRESS_ID = 200L;

    @Mock
    private MemberAddressMapper memberAddressMapper;

    private MemberAddressServiceImpl memberAddressService;

    @BeforeEach
    void setUp() {
        memberAddressService = new MemberAddressServiceImpl(
                memberAddressMapper,
                new PhoneCipher("unit-test-phone-aes-key", "unit-test-phone-salt"));
    }

    @Test
    @DisplayName("首条地址自动设为默认")
    void firstAddressBecomesDefault() {
        when(memberAddressMapper.selectCount(any())).thenReturn(0L);
        when(memberAddressMapper.insert(any(MemberAddress.class))).thenAnswer(invocation -> {
            MemberAddress entity = invocation.getArgument(0);
            entity.setId(ADDRESS_ID);
            return 1;
        });

        AddressView view = memberAddressService.create(USER_ID, command(false));

        assertThat(view.id()).isEqualTo(ADDRESS_ID);
        assertThat(view.isDefault()).isTrue();
        assertThat(view.receiverPhone()).isEqualTo("139****0003");
        assertThat(view.receiverPhone()).doesNotContain("13900000003");
        verify(memberAddressMapper).clearDefault(USER_ID);

        ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
        verify(memberAddressMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isEqualTo(1);
        assertThat(captor.getValue().getReceiverPhoneCipher()).isNotEqualTo("13900000003");
    }

    @Test
    @DisplayName("地址数量达到 20 条上限时拒绝新增")
    void rejectWhenLimitExceeded() {
        when(memberAddressMapper.selectCount(any())).thenReturn(20L);

        assertBizCode(() -> memberAddressService.create(USER_ID, command(false)),
                ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        verify(memberAddressMapper, never()).insert(any(MemberAddress.class));
    }

    @Test
    @DisplayName("设为默认时先清除同用户旧默认，再置当前地址为默认")
    void setDefaultClearsOldDefault() {
        when(memberAddressMapper.selectById(ADDRESS_ID)).thenReturn(address(USER_ID, 0));
        when(memberAddressMapper.update(any(MemberAddress.class), any())).thenReturn(1);

        memberAddressService.setDefault(USER_ID, ADDRESS_ID);

        verify(memberAddressMapper).clearDefault(USER_ID);
        ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
        verify(memberAddressMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getIsDefault()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新他人地址抛 10004，且不落更新")
    void updateOtherUsersAddressRejected() {
        when(memberAddressMapper.selectById(ADDRESS_ID)).thenReturn(address(OTHER_USER_ID, 1));

        assertBizCode(() -> memberAddressService.update(USER_ID, ADDRESS_ID, command(false)),
                ErrorCode.AUTH_FORBIDDEN);
        verify(memberAddressMapper, never()).update(any(MemberAddress.class), any());
    }

    @Test
    @DisplayName("删除他人地址抛 10004，且不落删除")
    void deleteOtherUsersAddressRejected() {
        when(memberAddressMapper.selectById(ADDRESS_ID)).thenReturn(address(OTHER_USER_ID, 1));

        assertBizCode(() -> memberAddressService.delete(USER_ID, ADDRESS_ID),
                ErrorCode.AUTH_FORBIDDEN);
        verify(memberAddressMapper, never()).deleteById(ADDRESS_ID);
    }

    @Test
    @DisplayName("删除默认地址后，剩余最新一条自动补为默认")
    void deleteDefaultPromotesLatestRemaining() {
        when(memberAddressMapper.selectById(ADDRESS_ID)).thenReturn(address(USER_ID, 1));
        when(memberAddressMapper.deleteById(ADDRESS_ID)).thenReturn(1);
        when(memberAddressMapper.selectOne(any())).thenReturn(address(PROMOTED_ADDRESS_ID, USER_ID, 0));
        when(memberAddressMapper.update(any(MemberAddress.class), any())).thenReturn(1);

        memberAddressService.delete(USER_ID, ADDRESS_ID);

        verify(memberAddressMapper).clearDefault(USER_ID);
        ArgumentCaptor<MemberAddress> captor = ArgumentCaptor.forClass(MemberAddress.class);
        verify(memberAddressMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getIsDefault()).isEqualTo(1);
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }

    private MemberAddress address(long userId, int isDefault) {
        return address(ADDRESS_ID, userId, isDefault);
    }

    private MemberAddress address(long id, long userId, int isDefault) {
        MemberAddress address = new MemberAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setIsDefault(isDefault);
        return address;
    }

    private SaveAddressCommand command(boolean isDefault) {
        return new SaveAddressCommand(
                "李四",
                "13900000003",
                "北京市",
                "北京市",
                "朝阳区",
                "望京SOHO T1",
                "1801",
                new BigDecimal("116.481499"),
                new BigDecimal("39.997632"),
                "公司",
                isDefault);
    }
}
