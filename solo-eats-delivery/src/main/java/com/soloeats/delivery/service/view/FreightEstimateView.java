package com.soloeats.delivery.service.view;

import java.util.List;

/**
 * 运费试算结果。deliveryFee 恒等于 breakdown 各项之和，单位分。
 */
public record FreightEstimateView(
        long deliveryFee,
        int distanceM,
        List<Item> breakdown
) {

    public record Item(String name, long amount) {
    }
}
