package com.soloeats.delivery.service.view;

/**
 * GEOSEARCH 结果。distanceM 为 Redis 返回的球面距离，单位米。
 */
public record NearbyRiderView(Long riderId, double distanceM) {
}
