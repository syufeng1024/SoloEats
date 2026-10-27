package com.soloeats.marketing.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * 拼手气红包二倍均值法，全部分值用 long 分计算。
 *
 * <p>每次分配上限取 {@code 2 * 剩余金额 / 剩余份数}，并额外压到
 * {@code 剩余金额 - (剩余份数 - 1)}，保证后面每份至少还能拿到 1 分。
 * 最后一份直接取剩余全额，保证 Σ 精确等于 total_amount。
 */
public final class RedPacketSplitter {

    private RedPacketSplitter() {
    }

    /**
     * 从剩余金额中抽一份。
     *
     * @param remainingAmount 剩余金额，分
     * @param remainingQty    剩余份数（含本次）
     */
    public static long draw(long remainingAmount, int remainingQty, RandomGenerator random) {
        if (remainingQty < 1 || remainingAmount < remainingQty) {
            throw new IllegalArgumentException("remaining amount must cover every remaining share");
        }
        if (remainingQty == 1) {
            return remainingAmount;
        }
        long twiceAverage = (remainingAmount / remainingQty) * 2L;
        long cap = remainingAmount - (remainingQty - 1L);
        long max = Math.min(twiceAverage, cap);
        if (max < 1L) {
            max = 1L;
        }
        return 1L + random.nextLong(max);
    }

    /** 生产入口：使用 ThreadLocalRandom。 */
    public static List<Long> split(long total, int qty) {
        return split(total, qty, ThreadLocalRandom.current());
    }

    /** 可注入随机源的纯函数，供单测断言总额守恒。 */
    public static List<Long> split(long total, int qty, RandomGenerator random) {
        if (qty < 1) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (total < qty) {
            throw new IllegalArgumentException("total must be at least qty");
        }
        List<Long> result = new ArrayList<>(qty);
        long remaining = total;
        int remainingQty = qty;
        while (remainingQty > 1) {
            long amount = draw(remaining, remainingQty, random);
            result.add(amount);
            remaining -= amount;
            remainingQty--;
        }
        result.add(remaining);
        return result;
    }
}
