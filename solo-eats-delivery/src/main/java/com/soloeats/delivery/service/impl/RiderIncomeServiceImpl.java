package com.soloeats.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.FreightRule;
import com.soloeats.delivery.domain.entity.Rider;
import com.soloeats.delivery.domain.entity.RiderFeeRule;
import com.soloeats.delivery.domain.entity.RiderIncome;
import com.soloeats.delivery.domain.entity.ShopFreightRel;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.FreightRuleMapper;
import com.soloeats.delivery.mapper.RiderFeeRuleMapper;
import com.soloeats.delivery.mapper.RiderIncomeMapper;
import com.soloeats.delivery.mapper.RiderMapper;
import com.soloeats.delivery.mapper.ShopFreightRelMapper;
import com.soloeats.delivery.service.RiderIncomeService;
import com.soloeats.delivery.service.view.RiderIncomeDetailView;
import com.soloeats.delivery.service.view.RiderIncomeOverview;
import com.soloeats.delivery.support.FreightConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderIncomeServiceImpl implements RiderIncomeService {

    static final long DEFAULT_TEMPLATE_ID = 1L;
    static final int RULE_TIME = 2;
    static final int RULE_WEATHER = 3;
    private static final int STATUS_ENABLED = 0;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final long METERS_PER_KM = 1000L;

    private final RiderIncomeMapper riderIncomeMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final RiderFeeRuleMapper riderFeeRuleMapper;
    private final FreightRuleMapper freightRuleMapper;
    private final ShopFreightRelMapper shopFreightRelMapper;
    private final RiderMapper riderMapper;

    @Override
    public RiderIncomeOverview overview(Long riderId) {
        if (riderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "riderId 不能为空");
        }
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> row = riderIncomeMapper.selectOverview(
                riderId, today, weekStart, monthStart);
        Rider rider = riderMapper.selectById(riderId);
        BigDecimal onTimeRate = rider == null || rider.getOnTimeRate() == null
                ? BigDecimal.ZERO : rider.getOnTimeRate();
        return new RiderIncomeOverview(
                summaryLong(row, "todayAmount"),
                summaryLong(row, "todayOrders"),
                summaryLong(row, "weekAmount"),
                summaryLong(row, "monthAmount"),
                onTimeRate);
    }

    @Override
    public PageResult<RiderIncomeDetailView> details(Long riderId, LocalDate dateFrom,
                                                     LocalDate dateTo, long pageNum, long pageSize) {
        if (riderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "riderId 不能为空");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L
                ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<RiderIncome> wrapper = new LambdaQueryWrapper<RiderIncome>()
                .eq(RiderIncome::getRiderId, riderId)
                .ge(dateFrom != null, RiderIncome::getSettlementDate, dateFrom)
                .le(dateTo != null, RiderIncome::getSettlementDate, dateTo)
                .orderByDesc(RiderIncome::getSettlementDate)
                .orderByDesc(RiderIncome::getId);
        Page<RiderIncome> page = riderIncomeMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<RiderIncomeDetailView> views = page.getRecords().stream()
                .map(RiderIncomeServiceImpl::toView)
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(Long deliveryId) {
        DeliveryOrder delivery = deliveryId == null ? null : deliveryOrderMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (delivery.getRiderId() == null) {
            log.warn("skip rider income settle, delivery has no rider deliveryId={}", deliveryId);
            return;
        }

        LocalDate today = LocalDate.now();
        RiderFeeRule rule = riderFeeRuleMapper.findActiveByRider(delivery.getRiderId(), today);
        if (rule == null) {
            rule = riderFeeRuleMapper.findActiveGlobal(today);
        }
        if (rule == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "骑手配送费规则未配置");
        }

        // 补贴按送达时刻判定：高峰窗口与天气开关取自门店运费模板的配置项
        LocalDateTime finishedAt = delivery.getDeliveredAt() == null
                ? LocalDateTime.now() : delivery.getDeliveredAt();
        List<FreightRule> shopRules = loadFreightRules(delivery.getShopId());
        long baseFee = nonNegative(rule.getBaseFee());
        long distanceFee = distanceFee(delivery.getDistanceM(), rule);
        long timeSubsidy = hasPeakWindow(shopRules, finishedAt.toLocalTime())
                ? nonNegative(rule.getPeakDelta()) : 0L;
        long weatherSubsidy = isBadWeather(shopRules)
                ? nonNegative(rule.getBadWeatherDelta()) : 0L;

        RiderIncome existing = findByDelivery(deliveryId);
        if (existing == null) {
            RiderIncome income = new RiderIncome();
            income.setRiderId(delivery.getRiderId());
            income.setDeliveryOrderId(deliveryId);
            income.setOrderId(delivery.getOrderId());
            income.setTipAmount(0L);
            income.setPenaltyAmount(0L);
            income.setCompensationAmount(0L);
            income.setSettlementDate(today);
            income.setStatus(0);
            applyAmounts(income, baseFee, distanceFee, timeSubsidy, weatherSubsidy);
            try {
                riderIncomeMapper.insert(income);
                log.info("rider income created deliveryId={} riderId={} total={}",
                        deliveryId, delivery.getRiderId(), income.getTotalAmount());
                return;
            } catch (DuplicateKeyException ex) {
                // 并发送达由 uk_delivery 兜底，转为更新已存在的行
                existing = findByDelivery(deliveryId);
                if (existing == null) {
                    throw new BizException(ErrorCode.SYSTEM_ERROR, "骑手收入写入冲突");
                }
            }
        }
        applyAmounts(existing, baseFee, distanceFee, timeSubsidy, weatherSubsidy);
        riderIncomeMapper.updateById(existing);
        log.info("rider income updated deliveryId={} riderId={} total={}",
                deliveryId, delivery.getRiderId(), existing.getTotalAmount());
    }

    private RiderIncome findByDelivery(Long deliveryId) {
        return riderIncomeMapper.selectOne(new LambdaQueryWrapper<RiderIncome>()
                .eq(RiderIncome::getDeliveryOrderId, deliveryId));
    }

    /**
     * 距离费 = ceil(distanceM / 1000) * per_km_fee；不足 1km 按 1km，0 米不计费。
     */
    private static long distanceFee(Integer distanceM, RiderFeeRule rule) {
        int meters = distanceM == null || distanceM < 0 ? 0 : distanceM;
        if (meters == 0) {
            return 0L;
        }
        long km = (meters + METERS_PER_KM - 1) / METERS_PER_KM;
        return km * nonNegative(rule.getPerKmFee());
    }

    /**
     * 保留既有小费/罚款/补偿，重算基础、距离、时段、天气与合计。
     * 罚款超过收入时合计置 0，不向下一结算周期传导负数。
     */
    private static void applyAmounts(RiderIncome income, long baseFee, long distanceFee,
                                     long timeSubsidy, long weatherSubsidy) {
        long tip = nonNegative(income.getTipAmount());
        long penalty = nonNegative(income.getPenaltyAmount());
        long compensation = nonNegative(income.getCompensationAmount());
        income.setBaseFee(baseFee);
        income.setDistanceFee(distanceFee);
        income.setTimeSubsidy(timeSubsidy);
        income.setWeatherSubsidy(weatherSubsidy);
        income.setTipAmount(tip);
        income.setPenaltyAmount(penalty);
        income.setCompensationAmount(compensation);
        long total = baseFee + distanceFee + timeSubsidy + weatherSubsidy
                + tip - penalty + compensation;
        income.setTotalAmount(Math.max(0L, total));
    }

    private List<FreightRule> loadFreightRules(Long shopId) {
        if (shopId == null) {
            return List.of();
        }
        ShopFreightRel rel = shopFreightRelMapper.findByShopId(shopId);
        Long templateId = rel == null || rel.getTemplateId() == null
                ? DEFAULT_TEMPLATE_ID : rel.getTemplateId();
        return freightRuleMapper.selectList(new LambdaQueryWrapper<FreightRule>()
                .eq(FreightRule::getTemplateId, templateId)
                .eq(FreightRule::getStatus, STATUS_ENABLED)
                .orderByAsc(FreightRule::getSort));
    }

    private static boolean hasPeakWindow(List<FreightRule> rules, LocalTime time) {
        for (FreightRule rule : rules) {
            if (!Objects.equals(rule.getRuleType(), RULE_TIME)) {
                continue;
            }
            JsonNode condition = FreightConditionEvaluator.parse(rule.getConditionJson());
            if (FreightConditionEvaluator.inWindows(condition, time)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBadWeather(List<FreightRule> rules) {
        for (FreightRule rule : rules) {
            if (Objects.equals(rule.getRuleType(), RULE_WEATHER)
                    && FreightConditionEvaluator.weatherEnabled(
                            FreightConditionEvaluator.parse(rule.getConditionJson()))) {
                return true;
            }
        }
        return false;
    }

    private static RiderIncomeDetailView toView(RiderIncome income) {
        return new RiderIncomeDetailView(
                income.getId(),
                income.getDeliveryOrderId(),
                income.getOrderId(),
                income.getBaseFee(),
                income.getDistanceFee(),
                income.getTimeSubsidy(),
                income.getWeatherSubsidy(),
                income.getTipAmount(),
                income.getPenaltyAmount(),
                income.getCompensationAmount(),
                income.getTotalAmount(),
                income.getSettlementDate(),
                income.getStatus(),
                income.getCreatedAt());
    }

    private static long nonNegative(Long value) {
        return value == null || value < 0L ? 0L : value;
    }

    private static long summaryLong(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return value == null ? 0L : Long.parseLong(value.toString());
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }
}
