package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.view.UserOrderView;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 退款终态展示口径符合性用例（D-04 · 方案 B）。
 *
 * <p>规格书 8.9 把「已退款」列为订单状态之一，但项目铁律
 * （`CLAUDE.md` 第 7 条：不得修改既有测试）与 `OrderStateMachineTest` 的
 * {@code REFUND_FULL_SUCCESS → CANCELLED} 断言锁死了状态机，不能新增状态码。
 *
 * <p>因此裁定为：**订单状态不动，退款语义由 `t_order.refund_status` 唯一承载**，
 * 展示层按 {@code refund_status=2（全额退款）} 把终态文案纠正为「已退款」。
 * 本用例锁定该展示契约，防止后续改动退回「已取消」口径。
 */
@ExtendWith(MockitoExtension.class)
class OrderRefundDisplayComplianceTest {

    private static final long USER_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long ORDER_ID = 91001L;

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
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Test
    @DisplayName("未完成即全额退款：状态 70 但文案为「已退款」而非「已取消」")
    void cancelledOrderWithFullRefund_showsRefunded() {
        assertThat(statusNameOf(OrderStatus.CANCELLED.getCode(), 2)).isEqualTo("已退款");
    }

    @Test
    @DisplayName("售后期内全额退款：状态 60 但文案为「已退款」而非「已完成」")
    void completedOrderWithAfterSalesRefund_showsRefunded() {
        assertThat(statusNameOf(OrderStatus.COMPLETED.getCode(), 2)).isEqualTo("已退款");
    }

    @Test
    @DisplayName("无退款的已取消订单仍显示「已取消」")
    void cancelledOrderWithoutRefund_showsCancelled() {
        assertThat(statusNameOf(OrderStatus.CANCELLED.getCode(), 0)).isEqualTo("已取消");
    }

    @Test
    @DisplayName("无退款的已完成订单仍显示「已完成」")
    void completedOrderWithoutRefund_showsCompleted() {
        assertThat(statusNameOf(OrderStatus.COMPLETED.getCode(), 0)).isEqualTo("已完成");
    }

    @Test
    @DisplayName("部分退款不覆盖状态文案，避免与全额退款混淆")
    void partialRefund_keepsOriginalLabel() {
        assertThat(statusNameOf(OrderStatus.CANCELLED.getCode(), 1)).isEqualTo("已取消");
    }

    @Test
    @DisplayName("备餐中订单不受退款标识影响")
    void preparingOrder_keepsOriginalLabel() {
        assertThat(statusNameOf(OrderStatus.PREPARING.getCode(), 0)).isEqualTo("备餐中");
    }

    @Test
    @DisplayName("待接单订单即使带退款标识也不被改写（退款尚未成功）")
    void waitAcceptOrder_keepsOriginalLabel() {
        assertThat(statusNameOf(OrderStatus.WAIT_ACCEPT.getCode(), 2)).isEqualTo("待接单");
    }

    private String statusNameOf(int status, int refundStatus) {
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Order> page = invocation.getArgument(0);
            page.setRecords(List.of(order(status, refundStatus)));
            page.setTotal(1L);
            return page;
        });
        when(orderItemMapper.selectMaps(any())).thenReturn(List.of());

        PageResult<UserOrderView> result = orderQueryService.pageUserOrders(USER_ID, null, 1, 10);
        return result.list().get(0).statusName();
    }

    private Order order(int status, int refundStatus) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO202610270001");
        order.setUserId(USER_ID);
        order.setMerchantId(10L);
        order.setShopId(SHOP_ID);
        order.setStatus(status);
        order.setRefundStatus(refundStatus);
        order.setRefundedAmount(refundStatus == 2 ? 5000L : 0L);
        order.setFulfillmentType(1);
        order.setGoodsAmount(5000L);
        order.setPackagingFee(0L);
        order.setDeliveryFee(0L);
        order.setPayAmount(5000L);
        order.setCreatedAt(LocalDateTime.now().minusHours(3));
        return order;
    }
}
