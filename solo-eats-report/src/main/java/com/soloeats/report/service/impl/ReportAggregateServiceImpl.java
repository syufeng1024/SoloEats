package com.soloeats.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.report.domain.entity.ReportDailyPlatform;
import com.soloeats.report.domain.entity.ReportDailyShop;
import com.soloeats.report.mapper.ReportDailyPlatformMapper;
import com.soloeats.report.mapper.ReportDailyShopMapper;
import com.soloeats.report.mapper.ReportSourceMapper;
import com.soloeats.report.mapper.ReportSourceMapper.CompletedOrderFact;
import com.soloeats.report.mapper.ReportSourceMapper.CreatedOrderFact;
import com.soloeats.report.mapper.ReportSourceMapper.RefundTotals;
import com.soloeats.report.mapper.ReportSourceMapper.ShopRatingFact;
import com.soloeats.report.mapper.ReportSourceMapper.ShopRefundFact;
import com.soloeats.report.service.ReportAggregateService;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 日报聚合实现。
 *
 * <p>口径：
 * <ul>
 *   <li>GMV = 已完成订单（status=60）pay_amount 之和，剔除全额退款（refund_status=2）；</li>
 *   <li>退款率分母取支付成功单数（t_payment_order.status=20）；</li>
 *   <li>佣金取 t_settlement_item.item_type=2，结算支出取 t_settlement_bill.payable_amount；</li>
 *   <li>补贴成本取订单 platform_discount；毛利润 = 佣金收入 - 补贴成本。</li>
 * </ul>
 *
 * <p>订单分页扫描，每页 1000 条、按 id 升序推进，避免把单日全量订单读进内存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAggregateServiceImpl implements ReportAggregateService {

    private static final int PAGE_SIZE = 1000;
    private static final int REFUND_STATUS_FULL = 2;

    private final ReportSourceMapper sourceMapper;
    private final ReportDailyPlatformMapper platformMapper;
    private final ReportDailyShopMapper shopMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ReportDailyPlatform buildPlatform(LocalDate statDate) {
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = statDate.plusDays(1).atStartOfDay();

        long orderCount = 0;
        long gmv = 0;
        long subsidyCost = 0;
        long lastId = 0;
        while (true) {
            List<CompletedOrderFact> batch = sourceMapper.scanCompletedOrders(start, end, lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (CompletedOrderFact fact : batch) {
                orderCount++;
                if (!Integer.valueOf(REFUND_STATUS_FULL).equals(fact.refundStatus())) {
                    gmv += zeroIfNull(fact.payAmount());
                    subsidyCost += zeroIfNull(fact.platformDiscount());
                }
            }
            lastId = batch.get(batch.size() - 1).id();
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }

        RefundTotals refunds = sourceMapper.refundTotals(start, end);
        long commissionIncome = sourceMapper.commissionIncome(statDate);
        long settlementExpense = sourceMapper.settlementExpense(statDate);

        ReportDailyPlatform row = new ReportDailyPlatform();
        row.setTenantId(TenantContext.get());
        row.setStatDate(statDate);
        row.setGmv(gmv);
        row.setOrderCount((int) orderCount);
        row.setPaidOrderCount(sourceMapper.countPaidOrders(start, end));
        row.setRefundAmount(zeroIfNull(refunds.refundAmount()));
        row.setRefundOrderCount((int) zeroIfNull(refunds.refundOrderCount()));
        row.setNewUserCount(sourceMapper.countNewUsers(start, end));
        row.setActiveUserCount(sourceMapper.countActiveUsers(start, end));
        row.setAvgOrderAmount(orderCount == 0 ? 0 : gmv / orderCount);
        row.setCommissionIncome(commissionIncome);
        row.setSettlementExpense(settlementExpense);
        row.setSubsidyCost(subsidyCost);
        row.setGrossProfit(commissionIncome - subsidyCost);
        return row;
    }

    @Override
    public void aggregate(LocalDate statDate) {
        ReportDailyPlatform platform = buildPlatform(statDate);
        List<ReportDailyShop> shops = buildShops(statDate);

        // 事务只包删除与插入，分页读在事务外完成，避免长时间持有连接与行锁。
        transactionTemplate.executeWithoutResult(status -> {
            platformMapper.delete(new LambdaQueryWrapper<ReportDailyPlatform>()
                    .eq(ReportDailyPlatform::getStatDate, statDate));
            shopMapper.delete(new LambdaQueryWrapper<ReportDailyShop>()
                    .eq(ReportDailyShop::getStatDate, statDate));
            platformMapper.insert(platform);
            for (ReportDailyShop shop : shops) {
                shopMapper.insert(shop);
            }
        });

        log.info("report daily aggregate done statDate={} gmv={} shops={}",
                statDate, platform.getGmv(), shops.size());
    }

    private List<ReportDailyShop> buildShops(LocalDate statDate) {
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = statDate.plusDays(1).atStartOfDay();
        Map<Long, ShopAccumulator> accumulators = new TreeMap<>();

        scanCompletedOrders(start, end, fact -> {
            ShopAccumulator acc = accumulators.computeIfAbsent(
                    fact.shopId(), id -> new ShopAccumulator(id, fact.merchantId()));
            acc.completedOrders++;
            if (!Integer.valueOf(REFUND_STATUS_FULL).equals(fact.refundStatus())) {
                acc.gmv += zeroIfNull(fact.payAmount());
            }
            if (fact.pickupReadyAt() != null && fact.createdAt() != null
                    && !fact.pickupReadyAt().isBefore(fact.createdAt())) {
                acc.prepMinutesSum += Duration.between(fact.createdAt(), fact.pickupReadyAt()).toMinutes();
                acc.prepCount++;
            }
            if (Integer.valueOf(2).equals(fact.fulfillmentType())) {
                acc.pickupOrders++;
            } else if (Integer.valueOf(1).equals(fact.fulfillmentType())) {
                acc.deliveryOrders++;
            }
        });

        scanCreatedOrders(start, end, fact -> {
            ShopAccumulator acc = accumulators.computeIfAbsent(
                    fact.shopId(), id -> new ShopAccumulator(id, fact.merchantId()));
            acc.totalOrders++;
            if (Integer.valueOf(70).equals(fact.status())) {
                acc.cancelledOrders++;
            }
        });

        for (ShopRefundFact refund : sourceMapper.shopRefunds(start, end)) {
            ShopAccumulator acc = accumulators.get(refund.shopId());
            if (acc != null) {
                acc.refundAmount = zeroIfNull(refund.refundAmount());
            }
        }
        for (ShopRatingFact rating : sourceMapper.shopRatings(start, end)) {
            ShopAccumulator acc = accumulators.get(rating.shopId());
            if (acc != null) {
                acc.rating = rating.rating();
            }
        }

        List<ReportDailyShop> result = new ArrayList<>(accumulators.size());
        for (ShopAccumulator acc : accumulators.values()) {
            ReportDailyShop row = new ReportDailyShop();
            row.setTenantId(TenantContext.get());
            row.setStatDate(statDate);
            row.setShopId(acc.shopId);
            row.setMerchantId(acc.merchantId);
            row.setOrderCount((int) acc.completedOrders);
            row.setGmv(acc.gmv);
            row.setRefundAmount(acc.refundAmount);
            row.setAvgPrepMinutes(acc.prepCount == 0
                    ? 0
                    : (int) Math.round((double) acc.prepMinutesSum / acc.prepCount));
            row.setCancelRate(percent(acc.cancelledOrders, acc.totalOrders));
            row.setRating(acc.rating == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : acc.rating.setScale(2, RoundingMode.HALF_UP));
            row.setPickupOrderCount(acc.pickupOrders);
            row.setDeliveryOrderCount(acc.deliveryOrders);
            result.add(row);
        }
        return result;
    }

    private void scanCompletedOrders(LocalDateTime start, LocalDateTime end,
                                     Consumer<CompletedOrderFact> consumer) {
        long lastId = 0;
        while (true) {
            List<CompletedOrderFact> batch = sourceMapper.scanCompletedOrders(start, end, lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(consumer);
            lastId = batch.get(batch.size() - 1).id();
            if (batch.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    private void scanCreatedOrders(LocalDateTime start, LocalDateTime end,
                                   Consumer<CreatedOrderFact> consumer) {
        long lastId = 0;
        while (true) {
            List<CreatedOrderFact> batch = sourceMapper.scanCreatedOrders(start, end, lastId, PAGE_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(consumer);
            lastId = batch.get(batch.size() - 1).id();
            if (batch.size() < PAGE_SIZE) {
                return;
            }
        }
    }

    /** 百分比数值，保留 2 位；分母为 0 时返回 0.00。 */
    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static final class ShopAccumulator {

        private final Long shopId;
        private final Long merchantId;
        private long completedOrders;
        private long gmv;
        private long refundAmount;
        private long prepMinutesSum;
        private int prepCount;
        private int pickupOrders;
        private int deliveryOrders;
        private int totalOrders;
        private int cancelledOrders;
        private BigDecimal rating;

        private ShopAccumulator(Long shopId, Long merchantId) {
            this.shopId = shopId;
            this.merchantId = merchantId;
        }
    }
}
