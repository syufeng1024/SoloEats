package com.soloeats.app.security;

import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.service.RiderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 业务主体 ID 解析：商家/骑手必须落到 merchant_id / t_rider.id，
 * 与 t_site_message.receiver_id、t_im_session.peer_id、WS 注册 key 同源。
 */
@ExtendWith(MockitoExtension.class)
class CurrentReceiverTest {

    private static final long USER_ID = 3L;
    private static final long MERCHANT_ID = 1L;
    private static final long RIDER_USER_ID = 7L;
    private static final long RIDER_ID = 88L;

    @Mock
    private RiderService riderService;

    private CurrentReceiver currentReceiver;

    @BeforeEach
    void setUp() {
        currentReceiver = new CurrentReceiver(riderService);
    }

    @Test
    @DisplayName("C_USER 的接收者 ID 就是登录 userId")
    void consumerUsesUserId() {
        UserPrincipal principal = principal(USER_ID, null, Set.of(Roles.C_USER));

        assertThat(currentReceiver.receiverId(principal)).isEqualTo(USER_ID);
        verifyNoInteractions(riderService);
    }

    @Test
    @DisplayName("商家使用 merchantId，而不是 t_sys_user.id")
    void merchantUsesMerchantId() {
        UserPrincipal principal = principal(4L, MERCHANT_ID, Set.of(Roles.MERCHANT));

        assertThat(currentReceiver.receiverId(principal)).isEqualTo(MERCHANT_ID);
        verifyNoInteractions(riderService);
    }

    @Test
    @DisplayName("骑手用 userId 反查 t_rider.id")
    void riderResolvesRiderIdByUserId() {
        UserPrincipal principal = principal(RIDER_USER_ID, null, Set.of(Roles.RIDER));
        when(riderService.resolveRiderId(RIDER_USER_ID, null)).thenReturn(RIDER_ID);

        assertThat(currentReceiver.receiverId(principal)).isEqualTo(RIDER_ID);
    }

    @Test
    @DisplayName("别名账号反查不到时回落令牌 riderId")
    void riderFallsBackToTokenRiderId() {
        UserPrincipal principal = new UserPrincipal(
                1L, RIDER_USER_ID, "rider1", null, null, RIDER_ID, Set.of(Roles.RIDER));
        when(riderService.resolveRiderId(RIDER_USER_ID, RIDER_ID)).thenReturn(RIDER_ID);

        assertThat(currentReceiver.receiverId(principal)).isEqualTo(RIDER_ID);
    }

    @Test
    @DisplayName("商家 token 缺 merchantId 抛 10004，不回落 userId")
    void merchantWithoutMerchantIdIsForbidden() {
        UserPrincipal principal = principal(4L, null, Set.of(Roles.MERCHANT));

        assertBizCode(() -> currentReceiver.receiverId(principal), ErrorCode.AUTH_FORBIDDEN);
        verifyNoInteractions(riderService);
    }

    @Test
    @DisplayName("骑手未建档抛 10004，而非 10002")
    void riderWithoutProfileIsForbidden() {
        UserPrincipal principal = principal(RIDER_USER_ID, null, Set.of(Roles.RIDER));
        when(riderService.resolveRiderId(RIDER_USER_ID, null))
                .thenThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND));

        assertBizCode(() -> currentReceiver.receiverId(principal), ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("平台后台使用登录 userId")
    void platformUsesUserId() {
        UserPrincipal principal = principal(9L, null, Set.of(Roles.ADMIN));

        assertThat(currentReceiver.receiverId(principal)).isEqualTo(9L);
        verifyNoInteractions(riderService);
    }

    private static UserPrincipal principal(Long userId, Long merchantId, Set<String> roles) {
        return new UserPrincipal(1L, userId, "tester", merchantId, null, null, roles);
    }

    private static void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                      ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
