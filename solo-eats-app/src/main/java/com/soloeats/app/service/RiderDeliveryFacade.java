package com.soloeats.app.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.RiderIncomeService;
import com.soloeats.delivery.service.command.TrackPointCommand;
import com.soloeats.delivery.service.view.DeliveryActionView;
import com.soloeats.order.service.DeliveryFulfillmentApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 骑手端取货/送达编排：配送单与订单必须在同一条链路上推进。
 * delivery 模块不依赖 order，因此跨域编排落在 app 层，控制器不再直接调用 RiderFulfillmentService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderDeliveryFacade {

    private final DeliveryService deliveryService;
    private final DeliveryFulfillmentApplicationService deliveryFulfillmentApplicationService;
    private final DeliveryTrackService deliveryTrackService;
    private final RiderIncomeService riderIncomeService;

    /**
     * 取货：配送单 20 -> 40 与订单 WAIT_RIDER_PICKUP -> DELIVERING 由订单侧应用服务在同一事务内完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryActionView pickup(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = requireRiderDelivery(deliveryId, riderId);
        deliveryFulfillmentApplicationService.pickup(delivery.getOrderId(), riderId);
        log.info("rider picked up deliveryId={} orderId={} riderId={}",
                deliveryId, delivery.getOrderId(), riderId);
        return actionView(deliveryId);
    }

    /**
     * 送达：订单侧应用服务把配送单推到 50、订单推到 60（DELIVERED -> AUTO_COMPLETE），
     * 随后补齐轨迹点与骑手收入结算。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryActionView delivered(Long deliveryId, Long riderId,
                                        BigDecimal lng, BigDecimal lat, Long photoFileId) {
        if ((lng == null) != (lat == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "经纬度必须同时提供");
        }
        DeliveryOrder delivery = requireRiderDelivery(deliveryId, riderId);
        if (Objects.equals(delivery.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            // 重复送达（如响应丢失后的重试）：订单侧已无法再次流转，只补结算与轨迹
            log.info("delivery already delivered, skip order transit deliveryId={} orderId={}",
                    deliveryId, delivery.getOrderId());
        } else {
            deliveryFulfillmentApplicationService.delivered(delivery.getOrderId(), riderId);
        }

        // 送达后按 t_rider_fee_rule 重算收入；DeliveryServiceImpl 已写入的兜底行在此原地更新
        riderIncomeService.settle(deliveryId);
        if (lng != null) {
            deliveryTrackService.report(riderId, List.of(new TrackPointCommand(
                    deliveryId, lng, lat, null, null, LocalDateTime.now())));
        }
        // 送达留证照片当前无落库字段（不得改既有表结构），只保留可追溯日志
        if (photoFileId != null) {
            log.info("delivery delivered with evidence photo deliveryId={} riderId={} photoFileId={}",
                    deliveryId, riderId, photoFileId);
        }
        log.info("rider delivered deliveryId={} orderId={} riderId={}",
                deliveryId, delivery.getOrderId(), riderId);
        return actionView(deliveryId);
    }

    private DeliveryOrder requireRiderDelivery(Long deliveryId, Long riderId) {
        DeliveryOrder delivery = deliveryService.getById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!Objects.equals(delivery.getRiderId(), riderId)) {
            throw new BizException(ErrorCode.DELIVERY_RIDER_MISMATCH);
        }
        return delivery;
    }

    private DeliveryActionView actionView(Long deliveryId) {
        DeliveryOrder latest = deliveryService.getById(deliveryId);
        if (latest == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        return new DeliveryActionView(
                latest.getId(), latest.getOrderId(), latest.getRiderId(), latest.getStatus());
    }
}
