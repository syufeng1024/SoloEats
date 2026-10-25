package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.service.view.RiderDeliveryView;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderDeliveryQueryServiceImplTest {

    private static final long RIDER_ID = 1L;
    private static final long OTHER_RIDER_ID = 2L;
    private static final long DELIVERY_ID = 70001L;
    private static final long ORDER_ID = 90001L;
    private static final long SHOP_ID = 10L;

    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;

    @InjectMocks
    private RiderDeliveryQueryServiceImpl riderDeliveryQueryService;

    /**
     * 纯单测没有 MyBatis 上下文，LambdaQueryWrapper 解析列名依赖 TableInfo 缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, DeliveryOrder.class);
    }

    @Test
    @DisplayName("列表：只查当前骑手配送单，并按接单时间倒序、id 兜底排序")
    void listOnlyReturnsOwnDeliveries() {
        stubPage(List.of(
                delivery(DELIVERY_ID, RIDER_ID, DeliveryOrderStatus.DELIVERING),
                delivery(70002L, RIDER_ID, DeliveryOrderStatus.DELIVERED)
        ));

        PageResult<RiderDeliveryView> result =
                riderDeliveryQueryService.pageMyDeliveries(RIDER_ID, null, 1, 10);

        assertThat(result.list()).extracting(RiderDeliveryView::deliveryId)
                .containsExactly(DELIVERY_ID, 70002L);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);

        LambdaQueryWrapper<DeliveryOrder> wrapper = captureWrapper();
        assertThat(wrapper.getSqlSegment()).contains("rider_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(RIDER_ID);
        // accepted_at 为空时用 created_at 兜底，不能退回只按 accepted_at 排序
        assertThat(wrapper.getSqlSegment())
                .contains("COALESCE(accepted_at, created_at)")
                .contains("DESC");
    }

    @Test
    @DisplayName("列表：status 非空时按配送单状态精确过滤")
    void listFiltersByStatus() {
        stubPage(List.of(delivery(DELIVERY_ID, RIDER_ID, DeliveryOrderStatus.DELIVERING)));

        PageResult<RiderDeliveryView> result = riderDeliveryQueryService.pageMyDeliveries(
                RIDER_ID, DeliveryOrderStatus.DELIVERING.getCode(), 1, 10);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).statusName()).isEqualTo("配送中");

        LambdaQueryWrapper<DeliveryOrder> wrapper = captureWrapper();
        assertThat(wrapper.getSqlSegment()).contains("rider_id").contains("status");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(RIDER_ID, DeliveryOrderStatus.DELIVERING.getCode());
    }

    @Test
    @DisplayName("详情：本人可查，越权字段返回 null 且状态名按枚举映射")
    void detailReturnsOwnDelivery() {
        DeliveryOrder delivery = delivery(DELIVERY_ID, RIDER_ID, DeliveryOrderStatus.DELIVERING);
        when(deliveryOrderMapper.selectById(DELIVERY_ID)).thenReturn(delivery);

        RiderDeliveryView view = riderDeliveryQueryService.detail(RIDER_ID, DELIVERY_ID);

        assertThat(view.deliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(view.deliveryNo()).isEqualTo("DL" + DELIVERY_ID);
        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        assertThat(view.shopId()).isEqualTo(SHOP_ID);
        assertThat(view.status()).isEqualTo(DeliveryOrderStatus.DELIVERING.getCode());
        assertThat(view.statusName()).isEqualTo("配送中");
        assertThat(view.pickupAddress()).isEqualTo("星光餐厅");
        assertThat(view.receiverAddress()).isEqualTo("1 号楼 101");
        assertThat(view.distanceM()).isEqualTo(1800);
        assertThat(view.deliveryFee()).isEqualTo(600L);
        assertThat(view.promiseDeliverAt()).isEqualTo(delivery.getPromiseDeliverAt());
        assertThat(view.acceptedAt()).isEqualTo(delivery.getAcceptedAt());
        assertThat(view.pickedUpAt()).isEqualTo(delivery.getPickedUpAt());
        assertThat(view.dispatchExpireAt()).isEqualTo(delivery.getDispatchExpireAt());
        // t_delivery_order 无收货人姓名/手机号列，只能返回 null
        assertThat(view.receiverName()).isNull();
        assertThat(view.receiverPhoneMask()).isNull();
    }

    @Test
    @DisplayName("详情：查他人配送单返回 10004")
    void detailRejectsOtherRidersDelivery() {
        when(deliveryOrderMapper.selectById(DELIVERY_ID))
                .thenReturn(delivery(DELIVERY_ID, OTHER_RIDER_ID, DeliveryOrderStatus.ACCEPTED));

        assertBizCode(() -> riderDeliveryQueryService.detail(RIDER_ID, DELIVERY_ID),
                ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("详情：配送单不存在返回 DELIVERY_NOT_FOUND")
    void detailMissingDelivery() {
        when(deliveryOrderMapper.selectById(DELIVERY_ID)).thenReturn(null);

        assertBizCode(() -> riderDeliveryQueryService.detail(RIDER_ID, DELIVERY_ID),
                ErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("列表：pageNum 0 归一为 1，pageSize 999 截断为 50")
    void listNormalizesPaging() {
        stubPage(List.of());

        PageResult<RiderDeliveryView> result =
                riderDeliveryQueryService.pageMyDeliveries(RIDER_ID, null, 0, 999);

        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(50);

        ArgumentCaptor<Page<DeliveryOrder>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(deliveryOrderMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(50);
    }

    @SuppressWarnings("unchecked")
    private void stubPage(List<DeliveryOrder> deliveries) {
        when(deliveryOrderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<DeliveryOrder> page = invocation.getArgument(0);
            page.setRecords(deliveries);
            page.setTotal(deliveries.size());
            return page;
        });
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<DeliveryOrder> captureWrapper() {
        ArgumentCaptor<Wrapper<DeliveryOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deliveryOrderMapper).selectPage(any(Page.class), captor.capture());
        return (LambdaQueryWrapper<DeliveryOrder>) captor.getValue();
    }

    private DeliveryOrder delivery(long id, long riderId, DeliveryOrderStatus status) {
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(id);
        delivery.setDeliveryNo("DL" + id);
        delivery.setOrderId(ORDER_ID);
        delivery.setShopId(SHOP_ID);
        delivery.setRiderId(riderId);
        delivery.setStatus(status.getCode());
        delivery.setPickupAddress("星光餐厅");
        delivery.setReceiverAddress("1 号楼 101");
        delivery.setDistanceM(1800);
        delivery.setDeliveryFee(600L);
        delivery.setDispatchExpireAt(acceptedAt.minusSeconds(15));
        delivery.setPromiseDeliverAt(acceptedAt.plusMinutes(30));
        delivery.setAcceptedAt(acceptedAt);
        delivery.setPickedUpAt(acceptedAt.plusMinutes(5));
        delivery.setCreatedAt(acceptedAt.minusMinutes(1));
        return delivery;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
