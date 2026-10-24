package com.soloeats.delivery.service;

import com.soloeats.delivery.domain.entity.DispatchConfig;
import com.soloeats.delivery.service.view.DispatchCandidate;

import java.util.List;

/**
 * 派单候选排序策略。纯函数式：只依赖入参，不查库、不读写 Redis，便于单测。
 */
public interface DispatchStrategy {

    /**
     * 按配置权重排序候选骑手，返回前 limit 名。
     * 权重全为 0 时保持传入顺序（稳定排序）。
     */
    List<DispatchCandidate> rank(List<DispatchCandidate> candidates, DispatchConfig config, int limit);
}
