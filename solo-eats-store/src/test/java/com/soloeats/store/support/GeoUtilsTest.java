package com.soloeats.store.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    /** 望京SOHO 附近两点，纬度差 0.001° ≈ 111m。 */
    private static final double BASE_LNG = 116.481499;
    private static final double BASE_LAT = 39.997632;

    @Test
    @DisplayName("Haversine：同一点距离为 0，纬度差 0.001° 约 111 米")
    void distanceMeters() {
        assertThat(GeoUtils.distanceMeters(BASE_LNG, BASE_LAT, BASE_LNG, BASE_LAT))
                .isCloseTo(0D, within(0.001D));

        double distance = GeoUtils.distanceMeters(BASE_LNG, BASE_LAT, BASE_LNG, BASE_LAT + 0.001D);
        assertThat(distance).isCloseTo(111.2D, within(2D));
    }

    @Test
    @DisplayName("圆形围栏：半径内命中，半径外不命中")
    void withinCircle() {
        // 纬度 +0.001° 约 111m
        assertThat(GeoUtils.withinCircle(BASE_LNG, BASE_LAT + 0.001D,
                BASE_LNG, BASE_LAT, 300)).isTrue();
        // 纬度 +0.01° 约 1112m
        assertThat(GeoUtils.withinCircle(BASE_LNG, BASE_LAT + 0.01D,
                BASE_LNG, BASE_LAT, 300)).isFalse();
        assertThat(GeoUtils.withinCircle(BASE_LNG, BASE_LAT, BASE_LNG, BASE_LAT, 0)).isFalse();
    }

    @Test
    @DisplayName("射线法：多边形内部命中，外部不命中")
    void pointInPolygon() {
        // 以门店为中心、边长约 222m 的正方形
        List<double[]> polygon = List.of(
                new double[]{BASE_LNG - 0.001D, BASE_LAT - 0.001D},
                new double[]{BASE_LNG + 0.001D, BASE_LAT - 0.001D},
                new double[]{BASE_LNG + 0.001D, BASE_LAT + 0.001D},
                new double[]{BASE_LNG - 0.001D, BASE_LAT + 0.001D});

        assertThat(GeoUtils.pointInPolygon(BASE_LNG, BASE_LAT, polygon)).isTrue();
        assertThat(GeoUtils.pointInPolygon(BASE_LNG + 0.002D, BASE_LAT, polygon)).isFalse();
        assertThat(GeoUtils.pointInPolygon(BASE_LNG, BASE_LAT + 0.002D, polygon)).isFalse();
    }

    @Test
    @DisplayName("射线法：凹多边形缺口区域不命中")
    void pointInConcavePolygon() {
        // 一个底部被挖空的凹多边形
        List<double[]> polygon = List.of(
                new double[]{0D, 0D},
                new double[]{4D, 0D},
                new double[]{4D, 4D},
                new double[]{3D, 4D},
                new double[]{3D, 1D},
                new double[]{1D, 1D},
                new double[]{1D, 4D},
                new double[]{0D, 4D});

        assertThat(GeoUtils.pointInPolygon(0.5D, 3D, polygon)).isTrue();
        assertThat(GeoUtils.pointInPolygon(2D, 3D, polygon)).isFalse();
        assertThat(GeoUtils.pointInPolygon(5D, 2D, polygon)).isFalse();
    }

    @Test
    @DisplayName("多边形少于 3 点视为不覆盖")
    void polygonWithTooFewPoints() {
        assertThat(GeoUtils.pointInPolygon(BASE_LNG, BASE_LAT, List.of(
                new double[]{BASE_LNG, BASE_LAT},
                new double[]{BASE_LNG + 0.001D, BASE_LAT}))).isFalse();
    }
}
