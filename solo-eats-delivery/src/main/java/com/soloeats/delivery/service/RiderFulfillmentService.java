package com.soloeats.delivery.service;

import com.soloeats.delivery.service.view.DeliveryActionView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手履约动作编排：只做归属校验与委托，配送单状态流转全部走 {@link DeliveryService}。
 */
public interface RiderFulfillmentService {

    /** 我已取货：校验配送单归属后委托 DeliveryService.pickup。 */
    DeliveryActionView pickup(Long deliveryId, Long riderId);

    /**
     * 确认送达：校验归属后委托 DeliveryService.deliver。
     * 携带经纬度时补一个轨迹点；photoFileId 当前无落库字段，只记日志。
     */
    DeliveryActionView deliver(Long deliveryId, Long riderId,
                               BigDecimal lng, BigDecimal lat, Long photoFileId);

    /**
     * 异常上报：写 t_rider_ping_anomaly，并在同一事务内写 t_order_exception。
     * type 取值 1~6，越界抛 10001。
     */
    void reportAnomaly(Long deliveryId, Long riderId, Integer type,
                       String remark, List<String> images);
}
