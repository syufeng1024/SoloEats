package com.soloeats.delivery.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.DeliveryTrack;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.DeliveryTrackMapper;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.command.TrackPointCommand;
import com.soloeats.delivery.service.view.TrackPointView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackServiceImplTest {

    private static final long RIDER_ID = 7L;
    private static final long OTHER_RIDER_ID = 99L;
    private static final long DELIVERY_ID = 1001L;
    private static final BigDecimal LNG = new BigDecimal("116.480000");
    private static final BigDecimal LAT = new BigDecimal("39.990000");

    @Mock
    private DeliveryTrackMapper deliveryTrackMapper;
    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private RiderOnlineService riderOnlineService;

    @InjectMocks
    private DeliveryTrackServiceImpl deliveryTrackService;

    @Test
    @DisplayName("单次上报超过 20 点直接拒绝，不触达数据库")
    void rejectMoreThan20Points() {
        List<TrackPointCommand> points = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> point(LNG, LAT, LocalDateTime.now().plusSeconds(i)))
                .toList();

        assertBizCode(() -> deliveryTrackService.report(RIDER_ID, points),
                ErrorCode.PARAM_INVALID);
        verifyNoInteractions(deliveryTrackMapper, deliveryOrderMapper);
    }

    @Test
    @DisplayName("上报非本人配送单抛 10004，且不落库")
    void rejectDeliveryOwnedByAnotherRider() {
        when(deliveryOrderMapper.selectById(DELIVERY_ID))
                .thenReturn(deliveryWithRider(OTHER_RIDER_ID));

        assertBizCode(() -> deliveryTrackService.report(
                        RIDER_ID, List.of(point(LNG, LAT, LocalDateTime.now()))),
                ErrorCode.AUTH_FORBIDDEN);
        verify(deliveryTrackMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("轨迹查询按 report_at 升序返回，limit<=0 默认取最近 100 点")
    void queryReturnsPointsAscending() {
        LocalDateTime base = LocalDateTime.now().withNano(0);
        DeliveryTrack newer = track(2L, base);
        DeliveryTrack older = track(1L, base.minusMinutes(2));
        when(deliveryTrackMapper.selectByDelivery(DELIVERY_ID, 100))
                .thenReturn(List.of(newer, older));

        List<TrackPointView> views = deliveryTrackService.queryByDelivery(DELIVERY_ID, 0);

        assertThat(views).extracting(TrackPointView::reportAt)
                .containsExactly(base.minusMinutes(2), base);
        assertThat(views).extracting(TrackPointView::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("最新点查询返回 report_at 最大的一条")
    void latestPositionReturnsLatestPoint() {
        LocalDateTime latestAt = LocalDateTime.now().withNano(0);
        when(deliveryTrackMapper.selectLatestByDelivery(DELIVERY_ID))
                .thenReturn(track(3L, latestAt));

        TrackPointView latest = deliveryTrackService.latestPosition(DELIVERY_ID);

        assertThat(latest).isNotNull();
        assertThat(latest.id()).isEqualTo(3L);
        assertThat(latest.reportAt()).isEqualTo(latestAt);
        verify(deliveryTrackMapper).selectLatestByDelivery(DELIVERY_ID);
    }

    @Test
    @DisplayName("合法批量上报一次落库，并用最后一点刷新 Redis 位置")
    void reportInsertsBatchAndRefreshesPosition() {
        when(deliveryOrderMapper.selectById(DELIVERY_ID))
                .thenReturn(deliveryWithRider(RIDER_ID));
        LocalDateTime first = LocalDateTime.now().withNano(0).minusMinutes(1);
        LocalDateTime second = first.plusSeconds(30);
        BigDecimal lastLng = new BigDecimal("116.490000");
        BigDecimal lastLat = new BigDecimal("39.995000");

        deliveryTrackService.report(RIDER_ID, List.of(
                point(first),
                point(lastLng, lastLat, second)));

        ArgumentCaptor<List<DeliveryTrack>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryTrackMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(DeliveryTrack::getRiderId)
                .containsOnly(RIDER_ID);
        verify(riderOnlineService).refreshPosition(RIDER_ID, lastLng, lastLat);
    }

    private static DeliveryOrder deliveryWithRider(Long riderId) {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(DELIVERY_ID);
        delivery.setOrderId(90001L);
        delivery.setRiderId(riderId);
        return delivery;
    }

    private static TrackPointCommand point(BigDecimal lng, BigDecimal lat,
                                           LocalDateTime reportAt) {
        return new TrackPointCommand(DELIVERY_ID, lng, lat, null, null, reportAt);
    }

    private static TrackPointCommand point(LocalDateTime reportAt) {
        return point(LNG, LAT, reportAt);
    }

    private static DeliveryTrack track(Long id, LocalDateTime reportAt) {
        DeliveryTrack track = new DeliveryTrack();
        track.setId(id);
        track.setDeliveryId(DELIVERY_ID);
        track.setRiderId(RIDER_ID);
        track.setLng(LNG);
        track.setLat(LAT);
        track.setReportAt(reportAt);
        return track;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
