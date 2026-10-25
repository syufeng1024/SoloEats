package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.OrderStatusLog;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.view.UserOrderDetailView;
import com.soloeats.order.service.view.UserOrderView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOrderQueryTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long ORDER_ID = 90001L;
    private static final long SECOND_ORDER_ID = 90002L;
    private static final long SHOP_ID = 1L;
    private static final String PLAIN_PHONE = "13800138000";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;

    @InjectMocks
    private OrderQueryServiceImpl orderQueryService;

    /**
     * 纯单测没有 MyBatis 上下文，LambdaQueryWrapper 解析列名依赖 TableInfo 缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        TableInfoHelper.initTableInfo(assistant, OrderStatusLog.class);
    }

    @Test
    @DisplayName("列表：只查当前用户订单，itemCount 走一次批量 SUM 查询")
    void listOnlyReturnsOwnOrders() {
        stubPage(List.of(
                order(ORDER_ID, USER_ID, OrderStatus.PREPARING.getCode()),
                order(SECOND_ORDER_ID, USER_ID, OrderStatus.COMPLETED.getCode())
        ));
        when(orderItemMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("order_id", ORDER_ID, "item_count", 3L),
                Map.of("order_id", SECOND_ORDER_ID, "item_count", 1L)
        ));

        PageResult<UserOrderView> result =
                orderQueryService.pageUserOrders(USER_ID, null, 1, 10);

        assertThat(result.list()).extracting(UserOrderView::orderId)
                .containsExactly(ORDER_ID, SECOND_ORDER_ID);
        assertThat(result.list().get(0).itemCount()).isEqualTo(3);
        assertThat(result.list().get(0).statusName()).isEqualTo("备餐中");
        assertThat(result.list().get(0).fulfillmentTypeName()).isEqualTo("自取");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);

        LambdaQueryWrapper<Order> wrapper = captureOrderWrapper();
        assertThat(wrapper.getSqlSegment()).contains("user_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(USER_ID);
        // 单次批量查询，禁止按订单循环（N+1）
        verify(orderItemMapper, times(1)).selectMaps(any());
        verify(orderItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("列表：status 非空时按状态精确过滤")
    void listFiltersByStatus() {
        stubPage(List.of(order(ORDER_ID, USER_ID, OrderStatus.PREPARING.getCode())));
        when(orderItemMapper.selectMaps(any())).thenReturn(List.of());

        PageResult<UserOrderView> result = orderQueryService.pageUserOrders(
                USER_ID, OrderStatus.PREPARING.getCode(), 1, 10);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).itemCount()).isZero();

        LambdaQueryWrapper<Order> wrapper = captureOrderWrapper();
        assertThat(wrapper.getSqlSegment()).contains("user_id").contains("status");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(USER_ID, OrderStatus.PREPARING.getCode());
    }

    @Test
    @DisplayName("列表：pageNum/pageSize 归一化，pageSize 上限 50")
    void listNormalizesPaging() {
        stubPage(List.of());

        PageResult<UserOrderView> result = orderQueryService.pageUserOrders(USER_ID, null, 0, 999);

        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(50);
        assertThat(result.list()).isEmpty();
    }

    @Test
    @DisplayName("详情：本人可查，含 items 与按时间升序的 timeline")
    void detailReturnsItemsAndAscendingTimeline() {
        Order order = order(ORDER_ID, USER_ID, OrderStatus.DELIVERING.getCode());
        order.setPickupCode("A123");
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1001L, "招牌饭", 2, 1200L, 2400L, 2200L),
                item(12L, 1002L, "冰红茶", 1, 500L, 500L, 500L)
        ));
        LocalDateTime created = LocalDateTime.of(2026, 10, 25, 12, 0);
        when(orderStatusLogMapper.selectList(any())).thenReturn(List.of(
                statusLog(-1, OrderStatus.WAIT_PAY.getCode(), "CREATE", created),
                statusLog(OrderStatus.WAIT_PAY.getCode(), OrderStatus.WAIT_ACCEPT.getCode(),
                        "PAY_SUCCESS", created.plusMinutes(1)),
                statusLog(OrderStatus.WAIT_ACCEPT.getCode(), OrderStatus.PREPARING.getCode(),
                        "MERCHANT_ACCEPT", created.plusMinutes(2))
        ));

        UserOrderDetailView view = orderQueryService.getUserOrder(USER_ID, ORDER_ID);

        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        assertThat(view.statusName()).isEqualTo("配送中");
        assertThat(view.pickupCode()).isEqualTo("A123");
        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).unitPrice()).isEqualTo(1200L);
        assertThat(view.items().get(0).payableAmount()).isEqualTo(2200L);
        assertThat(view.itemCount()).isEqualTo(3);
        assertThat(view.timeline()).hasSize(3);
        assertThat(view.timeline()).extracting(t -> t.createdAt())
                .isSorted();
        assertThat(view.timeline().get(0).event()).isEqualTo("CREATE");
        assertThat(view.timeline().get(0).fromStatusName()).isEqualTo("未知状态");
        assertThat(view.timeline().get(2).toStatusName()).isEqualTo("备餐中");

        LambdaQueryWrapper<OrderStatusLog> logWrapper = captureStatusLogWrapper();
        assertThat(logWrapper.getSqlSegment()).contains("created_at").contains("ASC");
    }

    @Test
    @DisplayName("详情：查他人订单返回 10004")
    void detailRejectsOtherUsersOrder() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(ORDER_ID, OTHER_USER_ID, OrderStatus.PREPARING.getCode()));

        assertBizCode(() -> orderQueryService.getUserOrder(USER_ID, ORDER_ID),
                ErrorCode.AUTH_FORBIDDEN);

        verifyNoInteractions(orderItemMapper, orderStatusLogMapper);
    }

    @Test
    @DisplayName("详情：订单不存在返回 ORDER_NOT_FOUND")
    void detailMissingOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertBizCode(() -> orderQueryService.getUserOrder(USER_ID, ORDER_ID),
                ErrorCode.ORDER_NOT_FOUND);

        verifyNoInteractions(orderItemMapper, orderStatusLogMapper);
    }

    @Test
    @DisplayName("详情：receiverPhone 返回掩码，不含明文")
    void detailMasksReceiverPhone() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(ORDER_ID, USER_ID, OrderStatus.WAIT_PAY.getCode()));
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(orderStatusLogMapper.selectList(any())).thenReturn(List.of());

        UserOrderDetailView view = orderQueryService.getUserOrder(USER_ID, ORDER_ID);

        assertThat(view.receiverPhoneMask()).isEqualTo("138****8000");
        assertThat(view.receiverPhoneMask()).isNotEqualTo(PLAIN_PHONE);
        assertThat(view.receiverPhoneMask()).doesNotContain(PLAIN_PHONE);
    }

    @SuppressWarnings("unchecked")
    private void stubPage(List<Order> orders) {
        when(orderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Order> page = invocation.getArgument(0);
            page.setRecords(orders);
            page.setTotal(orders.size());
            return page;
        });
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<Order> captureOrderWrapper() {
        ArgumentCaptor<Wrapper<Order>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(Page.class), captor.capture());
        return (LambdaQueryWrapper<Order>) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<OrderStatusLog> captureStatusLogWrapper() {
        ArgumentCaptor<Wrapper<OrderStatusLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderStatusLogMapper).selectList(captor.capture());
        return (LambdaQueryWrapper<OrderStatusLog>) captor.getValue();
    }

    private Order order(long id, long userId, int status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("SO" + id);
        order.setUserId(userId);
        order.setMerchantId(10L);
        order.setShopId(SHOP_ID);
        order.setStatus(status);
        order.setFulfillmentType(2);
        order.setGoodsAmount(3000L);
        order.setPackagingFee(200L);
        order.setDeliveryFee(0L);
        order.setPayAmount(3200L);
        order.setPickupCode("A" + id);
        order.setReceiverName("张三");
        order.setReceiverPhone(PLAIN_PHONE);
        order.setReceiverAddress("1 号楼 101");
        order.setRemark("少辣");
        order.setRefundStatus(0);
        order.setRefundedAmount(0L);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 25, 12, 0));
        return order;
    }

    private OrderItem item(long id, long skuId, String skuName, int quantity,
                           long unitPrice, long originalAmount, long payableAmount) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(ORDER_ID);
        item.setSkuId(skuId);
        item.setSkuName(skuName);
        item.setQuantity(quantity);
        item.setOriginalUnitPrice(unitPrice);
        item.setOriginalAmount(originalAmount);
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
        log.setOperatorType(1);
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
