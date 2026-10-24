package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.view.DeliveryTrackView;
import com.soloeats.order.service.OrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * C 端轨迹越权回归：只校验 C_USER 角色不够，必须校验配送单归属当前登录用户。
 */
@ExtendWith(MockitoExtension.class)
class ConsumerDeliveryAuthTest {

    private static final long DELIVERY_ID = 1001L;
    private static final long ORDER_ID = 9001L;
    private static final long OWNER_ID = 42L;
    private static final long OTHER_USER_ID = 43L;

    @Mock
    private DeliveryTrackService deliveryTrackService;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private OrderQueryService orderQueryService;

    @InjectMocks
    private ConsumerDeliveryController consumerDeliveryController;

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal(1L, OWNER_ID, "c-user",
                null, null, null, Set.of(Roles.C_USER)));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("非本人物流单返回 10004，不返回轨迹与骑手位置")
    void otherUsersDeliveryIsForbidden() {
        when(deliveryService.getById(DELIVERY_ID)).thenReturn(deliveryOf(ORDER_ID));
        when(orderQueryService.belongsToUser(ORDER_ID, OWNER_ID)).thenReturn(false);

        assertThatThrownBy(() -> consumerDeliveryController.track(DELIVERY_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN.getCode());
        verifyNoInteractions(deliveryTrackService);
    }

    @Test
    @DisplayName("本人订单正常返回轨迹")
    void ownDeliveryReturnsTrack() {
        DeliveryTrackView expected = new DeliveryTrackView(List.of(), null);
        when(deliveryService.getById(DELIVERY_ID)).thenReturn(deliveryOf(ORDER_ID));
        when(orderQueryService.belongsToUser(ORDER_ID, OWNER_ID)).thenReturn(true);
        when(deliveryTrackService.trackForConsumer(DELIVERY_ID)).thenReturn(expected);

        ApiResponse<DeliveryTrackView> response = consumerDeliveryController.track(DELIVERY_ID);

        assertThat(response.code()).isZero();
        assertThat(response.data()).isEqualTo(expected);
    }

    @Test
    @DisplayName("配送单不存在时返回 50003，不触发归属查询")
    void missingDeliveryIsNotFound() {
        when(deliveryService.getById(OTHER_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> consumerDeliveryController.track(OTHER_USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.DELIVERY_NOT_FOUND.getCode());
        verifyNoInteractions(orderQueryService, deliveryTrackService);
    }

    private static DeliveryOrder deliveryOf(Long orderId) {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(DELIVERY_ID);
        delivery.setOrderId(orderId);
        return delivery;
    }
}
