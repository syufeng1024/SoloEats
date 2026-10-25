package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.DiscountApportionItem;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠分摊纯函数：按各项 payableAmount 占 ΣpayableAmount 的比例分摊整数分，
 * 余数优先归折后金额最大的项，并保证：
 * <ul>
 *   <li>Σ分摊 = min(totalDiscount, ΣpayableAmount)；</li>
 *   <li>单项分摊不超过该项 payableAmount，不出现负数。</li>
 * </ul>
 * 不使用 double/float，比例乘法用 BigInteger 防溢出。
 */
public final class DiscountApportioner {

    private DiscountApportioner() {
    }

    public static List<DiscountApportionItem> apportion(List<DiscountApportionItem> items,
                                                         long totalDiscount) {
        if (items == null) {
            throw new IllegalArgumentException("items 不能为 null");
        }
        if (totalDiscount < 0) {
            throw new IllegalArgumentException("totalDiscount 不能为负数");
        }

        long totalPayable = 0L;
        for (DiscountApportionItem item : items) {
            if (item == null || item.payableAmount() < 0) {
                throw new IllegalArgumentException("payableAmount 不能为负数");
            }
            totalPayable = Math.addExact(totalPayable, item.payableAmount());
        }

        if (items.isEmpty() || totalPayable == 0L) {
            if (totalDiscount != 0L) {
                throw new IllegalArgumentException("没有可分摊的金额");
            }
            return copyWithDiscount(items, new long[items.size()]);
        }

        long effectiveTotal = Math.min(totalDiscount, totalPayable);
        long[] shares = new long[items.size()];
        long allocated = 0L;
        BigInteger denominator = BigInteger.valueOf(totalPayable);
        for (int i = 0; i < items.size(); i++) {
            long payable = items.get(i).payableAmount();
            shares[i] = BigInteger.valueOf(payable)
                    .multiply(BigInteger.valueOf(effectiveTotal))
                    .divide(denominator)
                    .longValueExact();
            allocated += shares[i];
        }

        // 余数逐分归入当前折后金额最大且仍有额度的项；金额相同时取序号靠前者，结果可复现。
        long remainder = effectiveTotal - allocated;
        while (remainder > 0) {
            int target = -1;
            long targetPayable = -1L;
            for (int i = 0; i < items.size(); i++) {
                long payable = items.get(i).payableAmount();
                if (shares[i] < payable && payable > targetPayable) {
                    target = i;
                    targetPayable = payable;
                }
            }
            if (target < 0) {
                break;
            }
            shares[target]++;
            remainder--;
        }

        return copyWithDiscount(items, shares);
    }

    private static List<DiscountApportionItem> copyWithDiscount(List<DiscountApportionItem> items,
                                                                long[] shares) {
        List<DiscountApportionItem> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            DiscountApportionItem item = items.get(i);
            result.add(new DiscountApportionItem(item.skuId(), item.payableAmount(), shares[i]));
        }
        return List.copyOf(result);
    }
}
