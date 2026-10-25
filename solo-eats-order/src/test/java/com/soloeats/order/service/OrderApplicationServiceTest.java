package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.member.service.view.OrderAddressView;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.statemachine.OrderStateMachine;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.service.SkuService;
import com.soloeats.product.service.StockService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long MERCHANT_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long ADDRESS_ID = 100L;
    private static final long SKU_ID = 1001L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderDiscountMapper orderDiscountMapper;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private SkuService skuService;
    @Mock
    private StockService stockService;
    @Mock
    private CouponService couponService;
    @Mock
    private MemberAddressService memberAddressService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    @Test
    @DisplayName("addressId 属于他人时返回 10004，不创建订单")
    void addressOwnedByAnotherUserRejected() {
        when(memberAddressService.resolveForOrder(OTHER_USER_ID, ADDRESS_ID))
                .thenThrow(new BizException(ErrorCode.AUTH_FORBIDDEN));

        assertBizCode(() -> orderApplicationService.createOrder(
                command(OTHER_USER_ID, ADDRESS_ID, null, null), "it-forbidden"), ErrorCode.AUTH_FORBIDDEN);

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("addressId 命中时用地址簿覆盖收货人、手机号、地址与坐标")
    void addressSnapshotOverridesRequestFields() {
        when(memberAddressService.resolveForOrder(USER_ID, ADDRESS_ID)).thenReturn(new OrderAddressView(
                ADDRESS_ID,
                "李四",
                "13900000003",
                "北京市北京市朝阳区望京SOHO T1 1801",
                new BigDecimal("116.481499"),
                new BigDecimal("39.997632")));
        stubSkuAndInsert();

        orderApplicationService.createOrder(
                command(USER_ID, ADDRESS_ID, new BigDecimal("1.0"), new BigDecimal("2.0")), "it-address");

        Order saved = captureInsertedOrder();
        assertThat(saved.getAddressId()).isEqualTo(ADDRESS_ID);
        assertThat(saved.getReceiverName()).isEqualTo("李四");
        assertThat(saved.getReceiverPhone()).isEqualTo("13900000003");
        assertThat(saved.getReceiverAddress()).isEqualTo("北京市北京市朝阳区望京SOHO T1 1801");
        assertThat(saved.getReceiverLng()).isEqualByComparingTo(new BigDecimal("116.481499"));
        assertThat(saved.getReceiverLat()).isEqualByComparingTo(new BigDecimal("39.997632"));
    }

    @Test
    @DisplayName("未传 addressId 时直接使用请求坐标")
    void requestCoordinatesKeptWithoutAddress() {
        stubSkuAndInsert();

        orderApplicationService.createOrder(
                command(USER_ID, null, new BigDecimal("116.490000"), new BigDecimal("39.990000")), "it-coord");

        Order saved = captureInsertedOrder();
        assertThat(saved.getAddressId()).isNull();
        assertThat(saved.getReceiverLng()).isEqualByComparingTo(new BigDecimal("116.490000"));
        assertThat(saved.getReceiverLat()).isEqualByComparingTo(new BigDecimal("39.990000"));
    }

    @Test
    @DisplayName("没有坐标时不抛异常，坐标保持为空")
    void missingCoordinatesAllowed() {
        stubSkuAndInsert();

        orderApplicationService.createOrder(command(USER_ID, null, null, null), "it-no-coord");

        Order saved = captureInsertedOrder();
        assertThat(saved.getReceiverLng()).isNull();
        assertThat(saved.getReceiverLat()).isNull();
    }

    private void stubSkuAndInsert() {
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setShopId(SHOP_ID);
        sku.setSpuId(10L);
        sku.setSkuName("招牌牛肉面");
        sku.setPrice(2800L);
        when(skuService.getRequired(SKU_ID, SHOP_ID)).thenReturn(sku);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1000L);
            return 1;
        });
    }

    private Order captureInsertedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        return captor.getValue();
    }

    private CreateOrderCommand command(Long userId, Long addressId, BigDecimal lng, BigDecimal lat) {
        return new CreateOrderCommand(
                userId,
                MERCHANT_ID,
                SHOP_ID,
                FulfillmentType.DELIVERY.getCode(),
                null,
                100L,
                300L,
                "张三",
                "13800000000",
                "旧地址",
                addressId,
                lng,
                lat,
                null,
                "少放辣",
                List.of(new CreateOrderCommand.ItemCommand(SKU_ID, 1)));
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
