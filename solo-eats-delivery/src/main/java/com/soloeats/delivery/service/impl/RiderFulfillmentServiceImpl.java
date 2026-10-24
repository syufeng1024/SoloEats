package com.soloeats.delivery.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.RiderPingAnomaly;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.RiderPingAnomalyMapper;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.OrderExceptionPort;
import com.soloeats.delivery.service.RiderFulfillmentService;
import com.soloeats.delivery.service.command.TrackPointCommand;
import com.soloeats.delivery.service.view.DeliveryActionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderFulfillmentServiceImpl implements RiderFulfillmentService {

    private static final int ANOMALY_TYPE_MIN = 1;
    private static final int ANOMALY_TYPE_MAX = 6;
    private static final int SOURCE_TYPE_RIDER = 3;
    private static final int ORDER_EXCEPTION_OTHER = 7;
    private static final int REMARK_MAX_LENGTH = 255;

    private final DeliveryService deliveryService;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryTrackService deliveryTrackService;
    private final RiderPingAnomalyMapper riderPingAnomalyMapper;
    private final OrderExceptionPort orderExceptionPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryActionView pickup(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = requireRiderDelivery(deliveryId, riderId);
        DeliveryOrder pickedUp = deliveryService.pickup(delivery.getOrderId(), riderId);
        log.info("rider picked up deliveryId={} riderId={}", deliveryId, riderId);
        return toView(pickedUp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryActionView deliver(Long deliveryId, Long riderId,
                                      BigDecimal lng, BigDecimal lat, Long photoFileId) {
        if ((lng == null) != (lat == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度必须同时提供");
        }
        DeliveryOrder delivery = requireRiderDelivery(deliveryId, riderId);
        DeliveryOrder delivered = deliveryService.deliver(delivery.getOrderId(), riderId);

        if (lng != null) {
            deliveryTrackService.report(riderId, List.of(new TrackPointCommand(
                    deliveryId, lng, lat, null, null, LocalDateTime.now())));
        }
        // 送达留证照片当前无落库字段（不得改既有表结构），只保留可追溯日志
        if (photoFileId != null) {
            log.info("delivery delivered with evidence photo deliveryId={} riderId={} photoFileId={}",
                    deliveryId, riderId, photoFileId);
        }
        log.info("rider delivered deliveryId={} riderId={}", deliveryId, riderId);
        return toView(delivered);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportAnomaly(Long deliveryId, Long riderId, Integer type,
                              String remark, List<String> images) {
        int anomalyType = type == null ? 0 : type;
        if (anomalyType < ANOMALY_TYPE_MIN || anomalyType > ANOMALY_TYPE_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "异常类型只能是 1~6");
        }
        DeliveryOrder delivery = requireRiderDelivery(deliveryId, riderId);

        RiderPingAnomaly anomaly = new RiderPingAnomaly();
        anomaly.setRiderId(riderId);
        anomaly.setDeliveryId(deliveryId);
        anomaly.setType(anomalyType);
        anomaly.setRemark(normalizeRemark(remark));
        anomaly.setImagesJson(toImagesJson(images));
        riderPingAnomalyMapper.insert(anomaly);

        orderExceptionPort.record(delivery.getOrderId(), deliveryId,
                toOrderExceptionType(anomalyType), SOURCE_TYPE_RIDER, riderId,
                normalizeRemark(remark));
        log.warn("rider reported anomaly deliveryId={} riderId={} type={}",
                deliveryId, riderId, anomalyType);
    }

    private DeliveryOrder requireRiderDelivery(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        return delivery;
    }

    /**
     * 骑手异常类型 → t_order_exception.exception_type：
     * 2联系不上→2、3地址错误→3、4餐品破损→4、5商家出餐慢→5；
     * 1超时与6用户拒收在既有枚举中无对应值，归入 7其他。
     */
    private static int toOrderExceptionType(int anomalyType) {
        return switch (anomalyType) {
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            default -> ORDER_EXCEPTION_OTHER;
        };
    }

    /** 去首尾空白与换行；空串归一为 null，超长截断到 255。 */
    private static String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > REMARK_MAX_LENGTH
                ? trimmed.substring(0, REMARK_MAX_LENGTH) : trimmed;
    }

    private String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "异常照片序列化失败");
        }
    }

    private static DeliveryActionView toView(DeliveryOrder delivery) {
        return new DeliveryActionView(
                delivery.getId(), delivery.getOrderId(), delivery.getRiderId(), delivery.getStatus());
    }
}
