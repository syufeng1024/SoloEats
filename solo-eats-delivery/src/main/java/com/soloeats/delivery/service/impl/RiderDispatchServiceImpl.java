package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.domain.entity.DispatchRecord;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.domain.enums.RiderCertStatus;
import com.soloeats.delivery.domain.enums.RiderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.DispatchRecordMapper;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DispatchConfigService;
import com.soloeats.delivery.service.DispatchStrategy;
import com.soloeats.delivery.service.RiderDispatchService;
import com.soloeats.delivery.service.RiderOnlineService;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.delivery.service.view.DeliveryActionView;
import com.soloeats.delivery.service.view.DispatchCandidate;
import com.soloeats.delivery.service.view.HallOrderView;
import com.soloeats.delivery.service.view.NearbyRiderView;
import com.soloeats.delivery.service.view.PendingDispatchView;
import com.soloeats.delivery.service.view.RiderPositionView;
import com.soloeats.store.support.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderDispatchServiceImpl implements RiderDispatchService {

    private static final int HALL_QUERY_LIMIT = 200;
    private static final int TOP_CANDIDATES = 5;
    private static final int DISPATCH_TYPE_AUTO = 1;
    private static final int RECORD_REJECTED = 2;
    private static final long LOCK_WAIT_SECONDS = 3L;
    private static final long LOCK_LEASE_SECONDS = 3L;

    private final DeliveryService deliveryService;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final RiderService riderService;
    private final RiderOnlineService riderOnlineService;
    private final DispatchConfigService dispatchConfigService;
    private final DispatchStrategy dispatchStrategy;
    private final DistributedLock distributedLock;

    @Override
    public List<PendingDispatchView> pendingDispatches(Long riderId) {
        List<DeliveryOrder> deliveries = deliveryOrderMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getRiderId, riderId)
                        .eq(DeliveryOrder::getStatus, DeliveryOrderStatus.ASSIGNED.getCode())
                        .orderByAsc(DeliveryOrder::getDispatchExpireAt));
        return deliveries.stream()
                .map(delivery -> new PendingDispatchView(
                        delivery.getId(),
                        delivery.getOrderId(),
                        delivery.getDeliveryNo(),
                        delivery.getShopId(),
                        delivery.getPickupAddress(),
                        delivery.getReceiverAddress(),
                        delivery.getDeliveryFee(),
                        remainingSeconds(delivery)))
                .toList();
    }

    @Override
    public List<HallOrderView> hall(Long riderId, BigDecimal lng, BigDecimal lat, String sortBy) {
        assertCanTakeOrder(riderId);
        LngLat origin = resolveOrigin(riderId, lng, lat);
        DispatchConfig config = dispatchConfigService.effectiveConfig(null);
        if (Integer.valueOf(0).equals(config.getGrabEnabled())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抢单大厅未开启");
        }
        int radiusM = requireRadius(config);

        List<DeliveryOrder> deliveries = deliveryOrderMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrder>()
                        .eq(DeliveryOrder::getStatus, DeliveryOrderStatus.WAIT_ASSIGN.getCode())
                        .isNull(DeliveryOrder::getRiderId)
                        .orderByDesc(DeliveryOrder::getId)
                        .last("LIMIT " + HALL_QUERY_LIMIT));

        List<HallOrderView> views = new ArrayList<>();
        for (DeliveryOrder delivery : deliveries) {
            LngLat target = resolveTarget(delivery);
            if (target == null) {
                continue;
            }
            double distance = GeoUtils.distanceMeters(
                    origin.lng(), origin.lat(), target.lng(), target.lat());
            if (distance > radiusM) {
                continue;
            }
            views.add(new HallOrderView(
                    delivery.getId(),
                    delivery.getDeliveryNo(),
                    delivery.getShopId(),
                    delivery.getPickupAddress(),
                    delivery.getReceiverAddress(),
                    (int) Math.round(distance),
                    delivery.getDeliveryFee(),
                    delivery.getCreatedAt()));
        }
        views.sort(hallComparator(sortBy));
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryActionView accept(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        if (Objects.equals(delivery.getStatus(), DeliveryOrderStatus.ACCEPTED.getCode())
                && Objects.equals(delivery.getRiderId(), riderId)) {
            return toView(delivery);
        }
        if (delivery.getRiderId() != null && !Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DISPATCH_TAKEN);
        }
        if (!Objects.equals(delivery.getStatus(), DeliveryOrderStatus.ASSIGNED.getCode())) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "只有已派单的配送单可以接单");
        }
        if (delivery.getDispatchExpireAt() != null
                && delivery.getDispatchExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.DISPATCH_EXPIRED);
        }

        assertCanTakeOrder(riderId);
        DeliveryOrder accepted = deliveryService.accept(delivery.getOrderId(), riderId);
        log.info("rider accepted delivery deliveryId={} riderId={}", deliveryId, riderId);
        return toView(accepted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long deliveryId, Long riderId, String reason) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        if (delivery.getRiderId() != null && !Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        if (!Objects.equals(delivery.getStatus(), DeliveryOrderStatus.ASSIGNED.getCode())) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "只有已派单的配送单可以拒单");
        }

        DispatchConfig config = dispatchConfigService.effectiveConfig(delivery.getShopId());
        if (config.getMaxRetry() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "派单最大重试次数未配置");
        }
        int maxRetry = config.getMaxRetry();
        int nextCount = (delivery.getDispatchCount() == null ? 0 : delivery.getDispatchCount()) + 1;
        boolean releaseToHall = nextCount >= maxRetry;

        DispatchRecord record = new DispatchRecord();
        record.setDeliveryOrderId(delivery.getId());
        record.setRiderId(riderId);
        record.setDispatchType(DISPATCH_TYPE_AUTO);
        record.setStatus(RECORD_REJECTED);
        record.setExpireAt(delivery.getDispatchExpireAt());
        record.setRespondedAt(LocalDateTime.now());
        dispatchRecordMapper.insert(record);

        deliveryService.reject(delivery.getOrderId(), riderId, releaseToHall);
        log.info("rider rejected delivery deliveryId={} riderId={} dispatchCount={} releaseToHall={}",
                deliveryId, riderId, nextCount, releaseToHall);
    }

    @Override
    public DeliveryActionView grab(Long deliveryId, Long riderId) {
        return distributedLock.executeWithLock(
                "lock:delivery:" + deliveryId, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,
                () -> doGrab(deliveryId, riderId));
    }

    @Override
    public void assertCanTakeOrder(Long riderId) {
        Rider rider = riderService.requireRider(riderId);
        if (!RiderCertStatus.isApproved(rider.getCertStatus())) {
            throw new BizException(ErrorCode.RIDER_NOT_CERTIFIED);
        }
        if (!Objects.equals(rider.getStatus(), RiderStatus.NORMAL.getCode())) {
            throw new BizException(ErrorCode.AUTH_ACCOUNT_DISABLED, "骑手账号已停用或拉黑");
        }
        if (!riderService.isHealthCertValid(rider.getHealthCertExpireAt(), LocalDate.now())) {
            throw new BizException(ErrorCode.RIDER_HEALTH_CERT_EXPIRED);
        }
        if (!riderOnlineService.isOnline(riderId)) {
            throw new BizException(ErrorCode.RIDER_OFFLINE);
        }
        Integer maxConcurrentOrders = rider.getMaxConcurrentOrders();
        if (maxConcurrentOrders == null || maxConcurrentOrders <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "骑手接单上限未配置");
        }
        if (riderOnlineService.getLoad(riderId) >= maxConcurrentOrders) {
            throw new BizException(ErrorCode.RIDER_ORDER_LIMIT);
        }
    }

    @Override
    public List<DispatchCandidate> rankCandidates(Long deliveryId) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        LngLat target = resolveTarget(delivery);
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "配送单缺少收货或取货坐标");
        }
        DispatchConfig config = dispatchConfigService.effectiveConfig(delivery.getShopId());
        int radiusM = requireRadius(config);
        long remaining = remainingSeconds(delivery);

        List<DispatchCandidate> candidates = new ArrayList<>();
        for (NearbyRiderView nearby : riderOnlineService.nearbyRiders(
                target.lng(), target.lat(), radiusM)) {
            Rider rider = null;
            try {
                rider = riderService.requireRider(nearby.riderId());
            } catch (BizException ex) {
                // Redis 里残留的已删除骑手直接跳过
                continue;
            }
            if (!RiderCertStatus.isApproved(rider.getCertStatus())
                    || !Objects.equals(rider.getStatus(), RiderStatus.NORMAL.getCode())
                    || !riderService.isHealthCertValid(rider.getHealthCertExpireAt(), LocalDate.now())) {
                continue;
            }
            candidates.add(new DispatchCandidate(
                    rider.getId(),
                    nearby.distanceM(),
                    riderOnlineService.getLoad(rider.getId()),
                    rider.getRating(),
                    remaining));
        }
        return dispatchStrategy.rank(candidates, config, TOP_CANDIDATES);
    }

    private DeliveryActionView doGrab(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        if (Objects.equals(delivery.getStatus(), DeliveryOrderStatus.ACCEPTED.getCode())
                && Objects.equals(delivery.getRiderId(), riderId)) {
            return toView(delivery);
        }
        if (delivery.getRiderId() != null) {
            throw new BizException(ErrorCode.DISPATCH_TAKEN);
        }
        if (!Objects.equals(delivery.getStatus(), DeliveryOrderStatus.WAIT_ASSIGN.getCode())) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "该配送单不在抢单大厅");
        }

        assertCanTakeOrder(riderId);
        DeliveryOrder accepted = deliveryService.accept(delivery.getOrderId(), riderId);
        log.info("rider grabbed delivery deliveryId={} riderId={}", deliveryId, riderId);
        return toView(accepted);
    }

    private LngLat resolveOrigin(Long riderId, BigDecimal lng, BigDecimal lat) {
        if (lng != null && lat != null) {
            return new LngLat(lng, lat);
        }
        RiderPositionView position = riderOnlineService.getPosition(riderId);
        if (position == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少骑手当前位置，请先上线并上报坐标");
        }
        return new LngLat(position.lng(), position.lat());
    }

    private static LngLat resolveTarget(DeliveryOrder delivery) {
        if (delivery.getPickupLng() != null && delivery.getPickupLat() != null) {
            return new LngLat(delivery.getPickupLng(), delivery.getPickupLat());
        }
        if (delivery.getReceiverLng() != null && delivery.getReceiverLat() != null) {
            return new LngLat(delivery.getReceiverLng(), delivery.getReceiverLat());
        }
        return null;
    }

    private static Comparator<HallOrderView> hallComparator(String sortBy) {
        Comparator<HallOrderView> byDistance = Comparator.comparing(
                HallOrderView::distanceM, Comparator.nullsLast(Integer::compareTo));
        if ("fee".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            HallOrderView::deliveryFee, Comparator.nullsLast(Long::compareTo))
                    .reversed()
                    .thenComparing(byDistance);
        }
        if ("time".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            HallOrderView::createdAt, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(byDistance);
        }
        return byDistance;
    }

    private DeliveryOrder requireDelivery(Long deliveryId) {
        DeliveryOrder delivery = deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

    /** 抢单半径只从配置读取；缺失视为配置不完整，不用 Java 默认值兜底。 */
    private static int requireRadius(DispatchConfig config) {
        Integer radiusM = config.getGrabRadiusM();
        if (radiusM == null || radiusM <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "抢单半径未配置");
        }
        return radiusM;
    }

    private static long remainingSeconds(DeliveryOrder delivery) {
        if (delivery.getDispatchExpireAt() == null) {
            return 0L;
        }
        return Math.max(Duration.between(LocalDateTime.now(), delivery.getDispatchExpireAt())
                .getSeconds(), 0L);
    }

    private static DeliveryActionView toView(DeliveryOrder delivery) {
        return new DeliveryActionView(
                delivery.getId(), delivery.getOrderId(), delivery.getRiderId(), delivery.getStatus());
    }

    private record LngLat(BigDecimal lng, BigDecimal lat) {
    }
}
