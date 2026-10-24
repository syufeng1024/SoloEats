package com.soloeats.delivery.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.RiderIncome;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.RiderIncomeMapper;
import com.soloeats.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final RiderIncomeMapper riderIncomeMapper;

    @Value("${app.dispatch.expire-seconds:15}")
    private int dispatchExpireSeconds;

    @Value("${app.dispatch.max-retry:3}")
    private int dispatchMaxRetry;

    @Override
    public DeliveryOrder getByOrderId(Long orderId) {
        return deliveryOrderMapper.findByOrderId(orderId);
    }

    @Override
    public DeliveryOrder getById(Long deliveryId) {
        return deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder dispatch(Long orderId) {
        DeliveryOrder existing = requireDelivery(orderId);
        if (existing.getRiderId() != null) {
            throw new BizException(ErrorCode.DISPATCH_TAKEN, "配送单已被骑手接单");
        }

        int status = existing.getStatus() == null ? -1 : existing.getStatus();
        if (status == DeliveryOrderStatus.CANCELLED.getCode()) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "配送单已取消");
        }
        if (status >= DeliveryOrderStatus.ACCEPTED.getCode()
                && status != DeliveryOrderStatus.REASSIGNED.getCode()) {
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "配送单已进入骑手履约阶段");
        }

        LocalDateTime now = LocalDateTime.now();
        if (status == DeliveryOrderStatus.ASSIGNED.getCode()
                && existing.getDispatchExpireAt() != null
                && existing.getDispatchExpireAt().isAfter(now)) {
            // 派单窗口内重复呼叫按幂等处理，不重复累加派单次数
            return existing;
        }

        int dispatchCount = existing.getDispatchCount() == null ? 0 : existing.getDispatchCount();
        if (dispatchCount >= dispatchMaxRetry) {
            throw new BizException(ErrorCode.DISPATCH_TAKEN, "配送单已达到最大派单次数");
        }

        LocalDateTime expireAt = now.plusSeconds(dispatchExpireSeconds);
        if (deliveryOrderMapper.dispatch(orderId, expireAt) != 1) {
            DeliveryOrder latest = requireDelivery(orderId);
            if (latest.getRiderId() != null) {
                throw new BizException(ErrorCode.DISPATCH_TAKEN, "配送单已被骑手接单");
            }
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID);
        }
        return requireDelivery(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createForOrder(Long orderId, Long shopId, String pickupAddress,
                               String receiverAddress, BigDecimal receiverLng,
                               BigDecimal receiverLat, Long deliveryFee) {
        if (deliveryOrderMapper.findByOrderId(orderId) != null) {
            return;
        }
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setDeliveryNo(generateDeliveryNo());
        delivery.setOrderId(orderId);
        delivery.setShopId(shopId);
        delivery.setStatus(DeliveryOrderStatus.WAIT_ASSIGN.getCode());
        delivery.setPickupAddress(pickupAddress);
        delivery.setReceiverAddress(receiverAddress);
        delivery.setReceiverLng(receiverLng);
        delivery.setReceiverLat(receiverLat);
        delivery.setDeliveryFee(deliveryFee == null ? 0L : deliveryFee);
        delivery.setDispatchCount(0);
        deliveryOrderMapper.insert(delivery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder accept(Long orderId, Long riderId) {
        DeliveryOrder existing = requireDelivery(orderId);
        if (Objects.equals(existing.getStatus(), DeliveryOrderStatus.ACCEPTED.getCode())
                && Objects.equals(existing.getRiderId(), riderId)) {
            return existing;
        }

        int rows = deliveryOrderMapper.accept(orderId, riderId);
        if (rows != 1) {
            DeliveryOrder latest = requireDelivery(orderId);
            if (latest.getRiderId() != null && !Objects.equals(latest.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.DISPATCH_TAKEN);
            }
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID);
        }
        return requireDelivery(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder pickup(Long orderId, Long riderId) {
        int rows = deliveryOrderMapper.pickup(orderId, riderId);
        if (rows != 1) {
            throwDeliveryState(orderId, riderId);
        }
        return requireDelivery(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder deliver(Long orderId, Long riderId) {
        DeliveryOrder existing = requireDelivery(orderId);
        if (Objects.equals(existing.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            if (!Objects.equals(existing.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
            }
            return existing;
        }

        int rows = deliveryOrderMapper.delivered(orderId, riderId);
        if (rows != 1) {
            throwDeliveryState(orderId, riderId);
        }

        DeliveryOrder delivered = requireDelivery(orderId);
        long fee = delivered.getDeliveryFee() == null ? 0L : delivered.getDeliveryFee();
        RiderIncome income = new RiderIncome();
        income.setRiderId(riderId);
        income.setDeliveryOrderId(delivered.getId());
        income.setOrderId(orderId);
        income.setBaseFee(fee);
        income.setDistanceFee(0L);
        income.setTimeSubsidy(0L);
        income.setWeatherSubsidy(0L);
        income.setTipAmount(0L);
        income.setPenaltyAmount(0L);
        income.setCompensationAmount(0L);
        income.setTotalAmount(fee);
        income.setSettlementDate(LocalDate.now());
        income.setStatus(0);
        riderIncomeMapper.insert(income);
        return delivered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder reject(Long orderId, Long riderId, boolean releaseToHall) {
        DeliveryOrder existing = requireDelivery(orderId);
        if (!Objects.equals(existing.getStatus(), DeliveryOrderStatus.ASSIGNED.getCode())) {
            if (existing.getRiderId() != null && !Objects.equals(existing.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
            }
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID);
        }
        if (existing.getRiderId() != null && !Objects.equals(existing.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }

        if (deliveryOrderMapper.reject(orderId, riderId, releaseToHall) != 1) {
            DeliveryOrder latest = requireDelivery(orderId);
            if (latest.getRiderId() != null && !Objects.equals(latest.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
            }
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID);
        }
        return requireDelivery(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryOrder releaseForReassign(Long orderId, Long riderId) {
        DeliveryOrder existing = requireDelivery(orderId);
        if (riderId == null || !Objects.equals(existing.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        if (deliveryOrderMapper.releaseForReassign(orderId, riderId) != 1) {
            DeliveryOrder latest = requireDelivery(orderId);
            if (!Objects.equals(latest.getRiderId(), riderId)) {
                throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
            }
            throw new BizException(ErrorCode.DELIVERY_STATE_INVALID, "当前配送单状态不允许改派");
        }
        return requireDelivery(orderId);
    }

    private DeliveryOrder requireDelivery(Long orderId) {
        DeliveryOrder delivery = deliveryOrderMapper.findByOrderId(orderId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return delivery;
    }

    private void throwDeliveryState(Long orderId, Long riderId) {
        DeliveryOrder latest = requireDelivery(orderId);
        if (!Objects.equals(latest.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        throw new BizException(ErrorCode.DELIVERY_STATE_INVALID);
    }

    private String generateDeliveryNo() {
        String time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "DL" + time + random;
    }
}
