package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.DispatchRecord;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.DispatchRecordMapper;
import com.soloeats.delivery.service.DeliveryAdminService;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.command.DeliveryQuery;
import com.soloeats.delivery.service.view.DeliveryOrderView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAdminServiceImpl implements DeliveryAdminService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int DISPATCH_TYPE_MANUAL = 3;
    private static final int RECORD_CANCELLED = 4;
    private static final Set<Integer> REASSIGNABLE_STATUS = Set.of(
            DeliveryOrderStatus.ASSIGNED.getCode(),
            DeliveryOrderStatus.ACCEPTED.getCode(),
            DeliveryOrderStatus.PICKED_UP.getCode(),
            DeliveryOrderStatus.DELIVERING.getCode());

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final DeliveryService deliveryService;

    @Override
    public PageResult<DeliveryOrderView> page(DeliveryQuery query, long pageNum, long pageSize) {
        DeliveryQuery condition = query == null
                ? new DeliveryQuery(null, null, null, null, null) : query;
        if (condition.dateFrom() != null && condition.dateTo() != null
                && condition.dateFrom().isAfter(condition.dateTo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }

        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<DeliveryOrder> wrapper = new LambdaQueryWrapper<DeliveryOrder>()
                .eq(condition.status() != null, DeliveryOrder::getStatus, condition.status())
                .eq(condition.shopId() != null, DeliveryOrder::getShopId, condition.shopId())
                .eq(condition.riderId() != null, DeliveryOrder::getRiderId, condition.riderId())
                .ge(condition.dateFrom() != null, DeliveryOrder::getCreatedAt,
                        condition.dateFrom() == null ? null : condition.dateFrom().atStartOfDay())
                .lt(condition.dateTo() != null, DeliveryOrder::getCreatedAt,
                        condition.dateTo() == null ? null
                                : condition.dateTo().plusDays(1).atStartOfDay())
                .orderByDesc(DeliveryOrder::getId);

        Page<DeliveryOrder> page = deliveryOrderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<DeliveryOrderView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassign(Long deliveryId, Long riderId, String reason, Long operatorId) {
        DeliveryOrder delivery = requireDelivery(deliveryId);
        if (delivery.getRiderId() == null) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "配送单尚未分配骑手，无需改派");
        }
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        if (!REASSIGNABLE_STATUS.contains(delivery.getStatus())) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "当前配送单状态不允许改派");
        }

        // 状态释放由 DeliveryService 受控方法完成，本类不写任何配送单状态 UPDATE
        deliveryService.releaseForReassign(delivery.getOrderId(), delivery.getRiderId());

        DispatchRecord record = new DispatchRecord();
        record.setDeliveryOrderId(delivery.getId());
        record.setRiderId(delivery.getRiderId());
        record.setDispatchType(DISPATCH_TYPE_MANUAL);
        record.setStatus(RECORD_CANCELLED);
        record.setExpireAt(delivery.getDispatchExpireAt());
        record.setRespondedAt(LocalDateTime.now());
        dispatchRecordMapper.insert(record);

        log.info("delivery reassigned deliveryId={} originalRiderId={} operatorId={} reason={}",
                deliveryId, delivery.getRiderId(), operatorId, normalizeReason(reason));
    }

    private DeliveryOrder requireDelivery(Long deliveryId) {
        DeliveryOrder delivery = deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DeliveryOrderView toView(DeliveryOrder delivery) {
        return new DeliveryOrderView(
                delivery.getId(),
                delivery.getDeliveryNo(),
                delivery.getOrderId(),
                delivery.getShopId(),
                delivery.getRiderId(),
                delivery.getStatus(),
                delivery.getPickupAddress(),
                delivery.getPickupLng(),
                delivery.getPickupLat(),
                delivery.getReceiverAddress(),
                delivery.getReceiverLng(),
                delivery.getReceiverLat(),
                delivery.getDistanceM(),
                delivery.getDeliveryFee(),
                delivery.getDispatchCount(),
                delivery.getPromiseDeliverAt(),
                delivery.getAcceptedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt());
    }
}
