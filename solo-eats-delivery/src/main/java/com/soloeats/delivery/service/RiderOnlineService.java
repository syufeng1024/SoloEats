package com.soloeats.delivery.service;

import com.soloeats.delivery.service.view.NearbyRiderView;
import com.soloeats.delivery.service.view.RiderPositionView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手在线状态与位置。实时数据只在 Redis：
 * {@code rider:geo}（GEO 集合，member=riderId）与
 * {@code rider:online:{riderId}}（Hash：lng/lat/lastPing/load，TTL 120s）。
 * 数据库 t_rider.online_status 只做兜底展示，不参与实时判定。
 */
public interface RiderOnlineService {

    void goOnline(Long riderId, BigDecimal lng, BigDecimal lat);

    void goOffline(Long riderId, String reason);

    boolean isOnline(Long riderId);

    /** 不在线或 key 已过期返回 null。 */
    RiderPositionView getPosition(Long riderId);

    /** GEOSEARCH 按距离升序，最多返回 50 人。 */
    List<NearbyRiderView> nearbyRiders(BigDecimal lng, BigDecimal lat, int radiusM);

    /** Redis Hash 中的 load；缺失按 0 处理。 */
    int getLoad(Long riderId);

    /**
     * 轨迹上报时刷新实时位置：更新 rider:geo 与 rider:online:{riderId} 的
     * lng/lat/lastPing 并续期 TTL。骑手已离线（key 过期）时直接返回，不复活在线状态。
     */
    void refreshPosition(Long riderId, BigDecimal lng, BigDecimal lat);
}
