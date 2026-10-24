package com.soloeats.delivery.service.impl;

import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.service.DispatchStrategy;
import com.soloeats.delivery.service.view.DispatchCandidate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 加权评分：
 * score = w_d * norm(1/distance) + w_l * norm(1/load) + w_r * norm(rating)
 *       + w_t * norm(remainingSeconds)
 *
 * <p>每项在候选集合内做 min-max 归一，权重全部来自 t_dispatch_config，
 * 此处不写死任何默认权重。归一区间为 0（min == max）时该项记 1.0，
 * 保证同分候选不会因除零被放大或压低。
 */
@Component
public class WeightedScoreStrategy implements DispatchStrategy {

    private static final double EPSILON = 1e-9;

    @Override
    public List<DispatchCandidate> rank(List<DispatchCandidate> candidates,
                                        DispatchConfig config,
                                        int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int size = candidates.size();
        double[] distanceScore = inverseNormalize(candidates.stream()
                .mapToDouble(candidate -> Math.max(candidate.distanceM(), 1D))
                .toArray());
        double[] loadScore = inverseNormalize(candidates.stream()
                .mapToDouble(candidate -> candidate.currentLoad() + 1D)
                .toArray());
        double[] ratingScore = minMaxNormalize(candidates.stream()
                .mapToDouble(candidate -> candidate.rating() == null
                        ? 0D : candidate.rating().doubleValue())
                .toArray());
        double[] timeoutScore = minMaxNormalize(candidates.stream()
                .mapToDouble(DispatchCandidate::remainingSeconds)
                .toArray());

        double distanceWeight = weight(config == null ? null : config.getDistanceWeight());
        double loadWeight = weight(config == null ? null : config.getLoadWeight());
        double ratingWeight = weight(config == null ? null : config.getRatingWeight());
        double timeoutWeight = weight(config == null ? null : config.getTimeoutWeight());

        List<Scored> scored = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double score = distanceWeight * distanceScore[i]
                    + loadWeight * loadScore[i]
                    + ratingWeight * ratingScore[i]
                    + timeoutWeight * timeoutScore[i];
            scored.add(new Scored(candidates.get(i), score));
        }
        // List.sort 是稳定排序：权重全 0 时同分项保持传入顺序
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        int max = limit <= 0 ? size : Math.min(limit, size);
        return scored.subList(0, max).stream().map(Scored::candidate).toList();
    }

    private static double weight(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }

    /** 对 1/x 做 min-max 归一：距离越近、负载越小得分越高。 */
    private static double[] inverseNormalize(double[] values) {
        double[] inverted = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            inverted[i] = 1D / values[i];
        }
        return minMaxNormalize(inverted);
    }

    private static double[] minMaxNormalize(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double[] normalized = new double[values.length];
        double span = max - min;
        for (int i = 0; i < values.length; i++) {
            normalized[i] = span < EPSILON ? 1D : (values[i] - min) / span;
        }
        return normalized;
    }

    private record Scored(DispatchCandidate candidate, double score) {
    }
}
