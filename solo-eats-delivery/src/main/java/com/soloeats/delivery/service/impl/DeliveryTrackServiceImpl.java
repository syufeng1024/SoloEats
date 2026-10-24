package com.soloeats.delivery.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.DeliveryTrack;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.DeliveryTrackMapper;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.command.TrackPointCommand;
import com.soloeats.delivery.service.view.DeliveryTrackView;
import com.soloeats.delivery.service.view.RiderPositionView;
import com.soloeats.delivery.service.view.TrackPointView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTrackServiceImpl implements DeliveryTrackService {

    static final int MAX_POINTS_PER_REPORT = 20;
    static final int DEFAULT_QUERY_LIMIT = 100;
    static final int MAX_QUERY_LIMIT = 1000;

    private static final BigDecimal MAX_METRIC = new BigDecimal("9999.99");

    private static final Comparator<TrackPointView> BY_REPORT_AT = Comparator
            .comparing(TrackPointView::reportAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TrackPointView::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final DeliveryTrackMapper deliveryTrackMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final RiderOnlineService riderOnlineService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void report(Long riderId, List<TrackPointCommand> points) {
        if (points == null || points.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "轨迹点不能为空");
        }
        if (points.size() > MAX_POINTS_PER_REPORT) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "一次最多上报 " + MAX_POINTS_PER_REPORT + " 个轨迹点");
        }
        if (riderId == null) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        Map<Long, DeliveryOrder> deliveryCache = new HashMap<>(4);
        List<DeliveryTrack> tracks = new ArrayList<>(points.size());
        TrackPointCommand lastPoint = null;
        LocalDateTime lastReportAt = null;

        for (TrackPointCommand point : points) {
            if (point == null || point.deliveryId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "deliveryId 不能为空");
            }
            validateCoordinate(point.lng(), point.lat());
            validateMetrics(point.speed(), point.accuracy());
            DeliveryOrder delivery = deliveryCache.computeIfAbsent(
                    point.deliveryId(), this::requireDelivery);
            if (!Objects.equals(delivery.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.AUTH_FORBIDDEN, "该配送单不属于当前骑手");
            }

            LocalDateTime reportAt = point.reportAt() == null ? LocalDateTime.now() : point.reportAt();
            DeliveryTrack track = new DeliveryTrack();
            track.setDeliveryId(point.deliveryId());
            track.setRiderId(riderId);
            track.setLng(point.lng());
            track.setLat(point.lat());
            track.setSpeed(point.speed() == null ? null
                    : point.speed().setScale(2, RoundingMode.HALF_UP));
            track.setAccuracyM(point.accuracy() == null ? null
                    : point.accuracy().setScale(2, RoundingMode.HALF_UP));
            track.setReportAt(reportAt);
            tracks.add(track);

            if (lastReportAt == null || reportAt.isAfter(lastReportAt)) {
                lastPoint = point;
                lastReportAt = reportAt;
            }
        }

        deliveryTrackMapper.insertBatch(tracks);
        if (lastPoint != null) {
            refreshRiderPositionQuietly(riderId, lastPoint.lng(), lastPoint.lat());
        }
    }

    @Override
    public List<TrackPointView> queryByDelivery(Long deliveryId, int limit) {
        requireDeliveryId(deliveryId);
        int normalizedLimit = limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, MAX_QUERY_LIMIT);
        return deliveryTrackMapper.selectByDelivery(deliveryId, normalizedLimit).stream()
                .map(this::toView)
                .sorted(BY_REPORT_AT)
                .toList();
    }

    @Override
    public TrackPointView latestPosition(Long deliveryId) {
        requireDeliveryId(deliveryId);
        DeliveryTrack latest = deliveryTrackMapper.selectLatestByDelivery(deliveryId);
        return latest == null ? null : toView(latest);
    }

    @Override
    public List<TrackPointView> replay(Long deliveryId) {
        requireDeliveryId(deliveryId);
        return deliveryTrackMapper.selectAllByDelivery(deliveryId).stream()
                .map(this::toView)
                .sorted(BY_REPORT_AT)
                .toList();
    }

    @Override
    public DeliveryTrackView trackForConsumer(Long deliveryId) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        List<TrackPointView> points = queryByDelivery(deliveryId, DEFAULT_QUERY_LIMIT);
        RiderPositionView position = delivery.getRiderId() == null ? null
                : riderOnlineService.getPosition(delivery.getRiderId());
        DeliveryTrackView.RiderPosition riderPosition = position == null ? null
                : new DeliveryTrackView.RiderPosition(
                        position.lng(), position.lat(), position.lastPing());
        return new DeliveryTrackView(points, riderPosition);
    }

    private DeliveryOrder requireDelivery(Long deliveryId) {
        DeliveryOrder delivery = deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

    private static void requireDeliveryId(Long deliveryId) {
        if (deliveryId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "deliveryId 不能为空");
        }
    }

    private static void validateCoordinate(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度不能为空");
        }
        double lngValue = lng.doubleValue();
        double latValue = lat.doubleValue();
        if (lngValue < -180D || lngValue > 180D || latValue < -90D || latValue > 90D) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度超出合法范围");
        }
    }

    /** speed / accuracy_m 为 decimal(6,2)，非负数且不超过 9999.99，避免严格模式截断报错。 */
    private static void validateMetrics(BigDecimal speed, BigDecimal accuracy) {
        validateMetric(speed, "speed");
        validateMetric(accuracy, "accuracy");
    }

    private static void validateMetric(BigDecimal value, String field) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(MAX_METRIC) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 超出合法范围");
        }
    }

    /** Redis 抖动不应回滚已落库的轨迹，降级为告警日志。 */
    private void refreshRiderPositionQuietly(Long riderId, BigDecimal lng, BigDecimal lat) {
        try {
            riderOnlineService.refreshPosition(riderId, lng, lat);
        } catch (RuntimeException ex) {
            log.warn("refresh rider position failed riderId={}", riderId, ex);
        }
    }

    private TrackPointView toView(DeliveryTrack track) {
        return new TrackPointView(
                track.getId(),
                track.getDeliveryId(),
                track.getRiderId(),
                track.getLng(),
                track.getLat(),
                track.getSpeed(),
                track.getAccuracyM(),
                track.getReportAt());
    }
}
