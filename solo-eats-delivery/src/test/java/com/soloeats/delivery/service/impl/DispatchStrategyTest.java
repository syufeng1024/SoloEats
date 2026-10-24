package com.soloeats.delivery.service.impl;

import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.service.view.DispatchCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchStrategyTest {

    private final WeightedScoreStrategy strategy = new WeightedScoreStrategy();

    @Test
    @DisplayName("距离权重生效：距离更近的骑手排前")
    void distanceWeightPrefersNearbyRider() {
        DispatchCandidate far = candidate(1L, 4000D, 0, "4.50", 10L);
        DispatchCandidate near = candidate(2L, 500D, 0, "4.50", 10L);

        List<DispatchCandidate> ranked = strategy.rank(
                List.of(far, near), config("1.00", "0", "0", "0"), 5);

        assertThat(ranked).extracting(DispatchCandidate::riderId)
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("负载权重生效：高负载骑手被压低")
    void highLoadRiderIsPenalized() {
        DispatchCandidate busy = candidate(1L, 800D, 6, "4.80", 10L);
        DispatchCandidate idle = candidate(2L, 800D, 0, "4.80", 10L);

        List<DispatchCandidate> ranked = strategy.rank(
                List.of(busy, idle), config("0", "1.00", "0", "0"), 5);

        assertThat(ranked).extracting(DispatchCandidate::riderId)
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("权重全 0 时保持传入顺序（稳定排序）")
    void zeroWeightsKeepInputOrder() {
        DispatchCandidate first = candidate(3L, 100D, 0, "5.00", 5L);
        DispatchCandidate second = candidate(1L, 900D, 4, "3.00", 60L);
        DispatchCandidate third = candidate(2L, 500D, 1, "4.00", 30L);

        List<DispatchCandidate> ranked = strategy.rank(
                List.of(first, second, third), config("0", "0", "0", "0"), 5);

        assertThat(ranked).extracting(DispatchCandidate::riderId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("最多返回 limit 名候选")
    void returnsAtMostLimit() {
        List<DispatchCandidate> candidates = List.of(
                candidate(1L, 100D, 0, "5.00", 10L),
                candidate(2L, 200D, 0, "5.00", 10L),
                candidate(3L, 300D, 0, "5.00", 10L),
                candidate(4L, 400D, 0, "5.00", 10L),
                candidate(5L, 500D, 0, "5.00", 10L),
                candidate(6L, 600D, 0, "5.00", 10L));

        List<DispatchCandidate> ranked = strategy.rank(
                candidates, config("1.00", "0", "0", "0"), 5);

        assertThat(ranked).hasSize(5);
    }

    private static DispatchCandidate candidate(Long riderId, double distanceM, int load,
                                               String rating, long remainingSeconds) {
        return new DispatchCandidate(
                riderId, distanceM, load, new BigDecimal(rating), remainingSeconds);
    }

    private static DispatchConfig config(String distance, String load,
                                         String rating, String timeout) {
        DispatchConfig config = new DispatchConfig();
        config.setDistanceWeight(new BigDecimal(distance));
        config.setLoadWeight(new BigDecimal(load));
        config.setRatingWeight(new BigDecimal(rating));
        config.setTimeoutWeight(new BigDecimal(timeout));
        return config;
    }
}
