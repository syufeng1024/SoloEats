package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠分摊不变量（规格书 9.7）与实付保底（规格书 9.8）。
 * 既有 {@link OrderApplicationServiceTest} 只覆盖收货信息解析，本类补金额口径。
 */
@ExtendWith(MockitoExtension.class)
class OrderApportionInvariantTest {

    private static final long USER_ID = 7L;
    private static final long MERCHANT_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long COUPON_ID = 9001L;

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
    @DisplayName("余数逐分归入仍有余量的项：Σ分摊 = 优惠额，单项不超该项应付")
    void remainderDistributedWithoutExceedingCapacity() {
        // 3 个 1 分商品 + 2 分优惠：按比例向下取整会得到 0/0/0，余数 2 分必须逐分落到有余量的项，
        // 旧实现「余数一次性塞最大项 + 超容量即抛异常」在这一口径下会失败。
        stubSkus(sku(1001L, 1L), sku(1002L, 1L), sku(1003L, 1L));
        stubOrderPersistence();
        when(couponService.lock(USER_ID, COUPON_ID, 1000L, 3L))
                .thenReturn(new CouponLockResult(COUPON_ID, 1L, 2L, 1));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderApplicationService.createOrder(pickupCommand(COUPON_ID, 1001L, 1002L, 1003L), "it-apportion-1");

        List<OrderItem> items = captureItems(3);
        long totalDiscount = items.stream().mapToLong(OrderItem::getDiscountAmount).sum();
        long totalPayable = items.stream().mapToLong(OrderItem::getPayableAmount).sum();
        assertThat(totalDiscount).isEqualTo(2L);
        assertThat(totalPayable).isEqualTo(1L);
        for (OrderItem item : items) {
            assertThat(item.getDiscountAmount()).isBetween(0L, item.getOriginalAmount());
            assertThat(item.getPayableAmount()).isEqualTo(item.getOriginalAmount() - item.getDiscountAmount());
        }
    }

    @Test
    @DisplayName("按折后应付比例分摊：明细合计 = 实付 - 打包费 - 配送费")
    void apportionKeepsItemTotalEqualToPayAmount() {
        stubSkus(sku(1001L, 1000L), sku(1002L, 2000L));
        stubOrderPersistence();
        when(couponService.lock(USER_ID, COUPON_ID, 1000L, 3000L))
                .thenReturn(new CouponLockResult(COUPON_ID, 1L, 1000L, 2));
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderApplicationService.createOrder(deliveryCommand(COUPON_ID, 1001L, 1002L), "it-apportion-2");

        Order order = captureOrder();
        List<OrderItem> items = captureItems(2);
        long totalPayable = items.stream().mapToLong(OrderItem::getPayableAmount).sum();
        assertThat(totalPayable).isEqualTo(3000L - 1000L);
        assertThat(order.getPayAmount()).isEqualTo(2000L);
        assertThat(items).extracting(OrderItem::getDiscountAmount)
                .containsExactlyInAnyOrder(333L, 667L);
    }

    @Test
    @DisplayName("未用券路径同样受实付保底约束：0 元成交被拒绝且不落库")
    void payFloorAppliesWithoutCoupon() {
        stubSkus(sku(1001L, 0L));

        assertThatThrownBy(() -> orderApplicationService.createOrder(
                pickupCommand(null, 1001L), "it-floor-1"))
                .isInstanceOf(BizException.class)
                .hasMessage("实付金额不能低于 0.01 元")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());

        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderItemMapper, never()).insert(any(OrderItem.class));
    }

    @Test
    @DisplayName("用券路径实付低于 1 分时同样被拒绝")
    void payFloorAppliesWithCoupon() {
        stubSkus(sku(1001L, 100L));
        stubOrderPersistence();
        when(couponService.lock(USER_ID, COUPON_ID, 1000L, 100L))
                .thenReturn(new CouponLockResult(COUPON_ID, 1L, 100L, 1));

        assertThatThrownBy(() -> orderApplicationService.createOrder(
                pickupCommand(COUPON_ID, 1001L), "it-floor-2"))
                .isInstanceOf(BizException.class)
                .hasMessage("实付金额不能低于 0.01 元")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());

        verify(orderItemMapper, never()).insert(any(OrderItem.class));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    private Sku sku(long id, long price) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setShopId(SHOP_ID);
        sku.setSpuId(10L);
        sku.setSkuName("菜品-" + id);
        sku.setPrice(price);
        return sku;
    }

    private void stubSkus(Sku... skus) {
        for (Sku sku : skus) {
            when(skuService.getRequired(sku.getId(), SHOP_ID)).thenReturn(sku);
        }
    }

    private void stubOrderPersistence() {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            ((Order) invocation.getArgument(0)).setId(1000L);
            return 1;
        });
    }

    private CreateOrderCommand pickupCommand(Long couponId, Long... skuIds) {
        return new CreateOrderCommand(
                USER_ID, MERCHANT_ID, SHOP_ID, FulfillmentType.PICKUP.getCode(), couponId,
                0L, 0L, null, null, null, null, null, null, "13800000000", null,
                toItems(skuIds));
    }

    private CreateOrderCommand deliveryCommand(Long couponId, Long... skuIds) {
        return new CreateOrderCommand(
                USER_ID, MERCHANT_ID, SHOP_ID, FulfillmentType.DELIVERY.getCode(), couponId,
                0L, 0L, "张三", "13800000000", "望京SOHO", null, null, null, null, null,
                toItems(skuIds));
    }

    private List<CreateOrderCommand.ItemCommand> toItems(Long... skuIds) {
        return java.util.Arrays.stream(skuIds)
                .map(skuId -> new CreateOrderCommand.ItemCommand(skuId, 1))
                .toList();
    }

    private Order captureOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper, atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    private List<OrderItem> captureItems(int expected) {
        ArgumentCaptor<OrderItem> captor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemMapper, times(expected)).insert(captor.capture());
        return captor.getAllValues();
    }
}
