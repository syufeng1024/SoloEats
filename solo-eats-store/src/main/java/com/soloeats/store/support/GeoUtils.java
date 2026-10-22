package com.soloeats.store.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 离线地理计算，不依赖第三方地图 SDK。
 * 距离单位米，地球半径取 6371000m；多边形判定用射线法。
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000D;

    private GeoUtils() {
    }

    /**
     * Haversine 球面距离。
     */
    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1D, Math.sqrt(a)));
    }

    public static double distanceMeters(BigDecimal lng1, BigDecimal lat1,
                                        BigDecimal lng2, BigDecimal lat2) {
        return distanceMeters(lng1.doubleValue(), lat1.doubleValue(),
                lng2.doubleValue(), lat2.doubleValue());
    }

    /**
     * 圆形围栏：点到圆心距离 &lt;= 半径。半径非正视为不覆盖。
     */
    public static boolean withinCircle(double lng, double lat,
                                       double centerLng, double centerLat, int radiusM) {
        if (radiusM <= 0) {
            return false;
        }
        return distanceMeters(lng, lat, centerLng, centerLat) <= radiusM;
    }

    /**
     * 射线法多边形判定。首尾点自动闭合；点数少于 3 返回 false。
     *
     * @param polygon 每项为 {@code [lng, lat]}
     */
    public static boolean pointInPolygon(double lng, double lat, List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        int size = polygon.size();
        boolean inside = false;
        for (int i = 0, j = size - 1; i < size; j = i++) {
            double xi = polygon.get(i)[0];
            double yi = polygon.get(i)[1];
            double xj = polygon.get(j)[0];
            double yj = polygon.get(j)[1];

            boolean crosses = (yi > lat) != (yj > lat);
            if (crosses && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
