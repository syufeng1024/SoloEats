package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.OrderStatusLog;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.view.MerchantDashboardView;
import com.soloeats.order.service.view.MerchantOrderDetailView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOrderQueryTest {

    private static final long MERCHANT_ID = 10L;
    private static final long ORDER_ID = 91001L;
    private static final long SHOP_ID = 1L;
    private static final String PLAIN_PHONE = "13800138000";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private OrderQueryServiceImpl orderQueryService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderStatusLog.class);
    }

    @Test
    @DisplayName("商家详情：items 与 timeline 齐全，手机号脱敏，paidAt 无列为 null")
    void merchantDetailReturnsItemsTimelineAndMaskedPhone() {
        Order order = pickupOrder(OrderStatus.WAIT_USER_PICKUP.getCode());
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                item(21L, 1001L, "招牌饭", 2, 1200L, 2400L),
                item(22L, 1002L, "冰红茶", 1, 500L, 500L)
        ));
        LocalDateTime created = LocalDateTime.of(2026, 10, 25, 12, 0);
        when(orderStatusLogMapper.selectList(any())).thenReturn(List.of(
                statusLog(-1, OrderStatus.WAIT_PAY.getCode(), "CREATE", created),
                statusLog(OrderStatus.PREPARING.getCode(), OrderStatus.WAIT_USER_PICKUP.getCode(),
                        "PREPARE_DONE", created.plusMinutes(20))
        ));

        MerchantOrderDetailView view = orderQueryService.getMerchantOrder(ORDER_ID);

        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        assertThat(view.orderNo()).isEqualTo("SO" + ORDER_ID);
        assertThat(view.shopId()).isEqualTo(SHOP_ID);
        assertThat(view.statusName()).isEqualTo("待用户取餐");
        assertThat(view.fulfillmentTypeName()).isEqualTo("自取");
        assertThat(view.pickupCode()).isEqualTo("A1234");
        assertThat(view.pickupReadyAt()).isEqualTo(created.plusMinutes(20));
        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).orderItemId()).isEqualTo(21L);
        assertThat(view.items().get(1).unitPrice()).isEqualTo(500L);
        assertThat(view.timeline()).hasSize(2);
        assertThat(view.timeline()).extracting(t -> t.createdAt()).isSorted();
        assertThat(view.receiverPhoneMask()).isEqualTo("138****8000");
        assertThat(view.receiverPhoneMask()).doesNotContain(PLAIN_PHONE);
        assertThat(view.paidAt()).isNull();
    }

    @Test
    @DisplayName("商家详情：订单不存在返回 ORDER_NOT_FOUND，不再查明细与流水")
    void merchantDetailMissingOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertBizCode(() -> orderQueryService.getMerchantOrder(ORDER_ID),
                ErrorCode.ORDER_NOT_FOUND);

        verifyNoInteractions(orderItemMapper, orderStatusLogMapper);
    }

    @Test
    @DisplayName("工作台：一次聚合查询回填 5 个计数")
    void dashboardReturnsAggregatedCounts() {
        when(orderMapper.selectMerchantDashboard(eq(MERCHANT_ID), isNull(), any()))
                .thenReturn(Map.of(
                        "today_order_count", 12L,
                        "pending_accept_count", 3L,
                        "preparing_count", 2L,
                        "wait_pickup_count", 1L,
                        "completed_today_count", 5L
                ));

        MerchantDashboardView view = orderQueryService.dashboard(MERCHANT_ID, null);

        assertThat(view.todayOrderCount()).isEqualTo(12L);
        assertThat(view.pendingAcceptCount()).isEqualTo(3L);
        assertThat(view.preparingCount()).isEqualTo(2L);
        assertThat(view.waitPickupCount()).isEqualTo(1L);
        assertThat(view.completedTodayCount()).isEqualTo(5L);
        verify(orderMapper).selectMerchantDashboard(
                MERCHANT_ID, null, LocalDate.now().atStartOfDay());
    }

    @Test
    @DisplayName("工作台：token 带 shopId 时按门店收窄")
    void dashboardNarrowsByShopWhenTokenHasShopId() {
        when(orderMapper.selectMerchantDashboard(MERCHANT_ID, SHOP_ID,
                LocalDate.now().atStartOfDay())).thenReturn(Map.of());

        MerchantDashboardView view = orderQueryService.dashboard(MERCHANT_ID, SHOP_ID);

        assertThat(view.todayOrderCount()).isZero();
        assertThat(view.completedTodayCount()).isZero();
        verify(orderMapper).selectMerchantDashboard(
                MERCHANT_ID, SHOP_ID, LocalDate.now().atStartOfDay());
    }

    private Order pickupOrder(int status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO" + ORDER_ID);
        order.setUserId(1L);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(SHOP_ID);
        order.setStatus(status);
        order.setFulfillmentType(2);
        order.setGoodsAmount(2900L);
        order.setPackagingFee(200L);
        order.setDeliveryFee(0L);
        order.setPayAmount(3100L);
        order.setPickupCode("A1234");
        order.setPickupReadyAt(LocalDateTime.of(2026, 10, 25, 12, 20));
        order.setReceiverName("张三");
        order.setReceiverPhone(PLAIN_PHONE);
        order.setReceiverAddress("1 号楼 101");
        order.setRemark("少辣");
        order.setCreatedAt(LocalDateTime.of(2026, 10, 25, 12, 0));
        return order;
    }

    private OrderItem item(long id, long skuId, String skuName, int quantity,
                           long unitPrice, long payableAmount) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(ORDER_ID);
        item.setSkuId(skuId);
        item.setSkuName(skuName);
        item.setQuantity(quantity);
        item.setOriginalUnitPrice(unitPrice);
        item.setPayableAmount(payableAmount);
        return item;
    }

    private OrderStatusLog statusLog(int fromStatus, int toStatus, String event,
                                     LocalDateTime createdAt) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(ORDER_ID);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setEvent(event);
        log.setOperatorType(2);
        log.setCreatedAt(createdAt);
        return log;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
