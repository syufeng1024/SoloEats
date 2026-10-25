package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.domain.entity.SettlementItem;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.AdminSettlementStatsResult;
import com.soloeats.settlement.service.view.AdminSettlementSummary;
import com.soloeats.settlement.service.view.SettlementBillView;
import com.soloeats.settlement.service.view.SettlementDetailResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import com.soloeats.settlement.service.view.SettlementItemView;
import com.soloeats.settlement.service.view.SettlementListResult;
import com.soloeats.settlement.service.view.SettlementOrderView;
import com.soloeats.settlement.service.view.SettlementSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementBillMapper settlementBillMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.settlement.default-commission-rate:0.0600}")
    private BigDecimal commissionRate;

    @Override
    public SettlementGenerateResult generateDailyBills() {
        LocalDate billDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .eq(Order::getSettlementStatus, 0)
                .eq(Order::getRefundStatus, 0)
                .eq(Order::getRefundedAmount, 0L)
                .isNotNull(Order::getSettleEligibleAt)
                .le(Order::getSettleEligibleAt, now)
                .orderByAsc(Order::getId));

        Map<Long, List<Order>> byMerchant = orders.stream()
                .filter(order -> order.getMerchantId() != null)
                .collect(Collectors.groupingBy(
                        Order::getMerchantId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Long> billIds = new ArrayList<>();
        int orderCount = 0;
        for (Map.Entry<Long, List<Order>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            if (settlementBillMapper.findByMerchantAndDate(merchantId, billDate) != null) {
                continue;
            }
            try {
                Long billId = transactionTemplate.execute(status ->
                        generateForMerchant(merchantId, billDate, entry.getValue()));
                if (billId != null) {
                    billIds.add(billId);
                    orderCount += entry.getValue().size();
                }
            } catch (DuplicateKeyException ex) {
                log.warn("settlement bill already exists merchant={} date={}", merchantId, billDate);
            }
        }
        return new SettlementGenerateResult(billIds.size(), orderCount, billIds);
    }

    @Scheduled(cron = "${app.settlement.bill-cron:0 0 2 * * ?}")
    public void scheduledGenerate() {
        SettlementGenerateResult result = generateDailyBills();
        log.info("settlement job finished bills={} orders={}", result.billCount(), result.orderCount());
    }

    @Override
    public SettlementListResult listMerchantBills(Long merchantId, Integer status) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        List<SettlementBill> bills = settlementBillMapper.selectList(new LambdaQueryWrapper<SettlementBill>()
                .eq(SettlementBill::getMerchantId, merchantId)
                .eq(status != null, SettlementBill::getStatus, status)
                .orderByDesc(SettlementBill::getBillDate)
                .orderByDesc(SettlementBill::getId));

        List<SettlementBillView> views = bills.stream().map(this::toBillView).toList();
        long gross = 0L;
        long merchantAmount = 0L;
        long platformCommission = 0L;
        long payable = 0L;
        for (SettlementBill bill : bills) {
            gross += nz(bill.getGrossAmount());
            merchantAmount += nz(bill.getPayableAmount());
            platformCommission += nz(bill.getCommissionAmount());
            payable += nz(bill.getPayableAmount());
        }
        return new SettlementListResult(
                views,
                new SettlementSummary(bills.size(), gross, merchantAmount, platformCommission, payable)
        );
    }

    @Override
    public SettlementDetailResult getBillDetail(Long merchantId, Long billId) {
        if (merchantId == null || billId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 和 billId 不能为空");
        }
        SettlementBill bill = settlementBillMapper.selectById(billId);
        if (bill == null || !Objects.equals(bill.getMerchantId(), merchantId)) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }

        List<SettlementItem> items = settlementItemMapper.selectList(
                new LambdaQueryWrapper<SettlementItem>()
                        .eq(SettlementItem::getBillId, billId)
                        .orderByAsc(SettlementItem::getId));

        Map<Long, List<SettlementItem>> byOrder = items.stream()
                .filter(item -> item.getOrderId() != null)
                .collect(Collectors.groupingBy(
                        SettlementItem::getOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<SettlementOrderView> orders = new ArrayList<>();
        for (Map.Entry<Long, List<SettlementItem>> entry : byOrder.entrySet()) {
            Order order = orderMapper.selectById(entry.getKey());
            if (order == null) {
                continue;
            }
            long commission = entry.getValue().stream()
                    .filter(item -> Objects.equals(item.getItemType(), 2))
                    .mapToLong(item -> -nz(item.getAmount()))
                    .sum();
            long merchantAmount = entry.getValue().stream()
                    .mapToLong(item -> nz(item.getAmount()))
                    .sum();
            orders.add(new SettlementOrderView(
                    order.getId(),
                    order.getOrderNo(),
                    order.getPayAmount(),
                    order.getGoodsAmount(),
                    order.getPackagingFee(),
                    order.getDeliveryFee(),
                    order.getMerchantDiscount(),
                    order.getPlatformDiscount(),
                    commission,
                    merchantAmount,
                    order.getCompletedAt(),
                    order.getSettleEligibleAt()
            ));
        }

        List<SettlementItemView> itemViews = items.stream()
                .map(item -> new SettlementItemView(
                        item.getId(),
                        item.getOrderId(),
                        item.getItemType(),
                        item.getAmount(),
                        item.getRemark()
                ))
                .toList();

        return new SettlementDetailResult(toBillView(bill), orders, itemViews);
    }

    @Override
    public AdminSettlementStatsResult adminStats(Long merchantId, Integer status,
                                                 LocalDate dateFrom, LocalDate dateTo,
                                                 long pageNum, long pageSize) {
        validateSettlementStatus(status);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = pageSize < 1L ? 20L : Math.min(pageSize, 100L);

        QueryWrapper<SettlementBill> pageWrapper = new QueryWrapper<>();
        applyAdminFilters(pageWrapper, merchantId, status, dateFrom, dateTo);
        pageWrapper.orderByDesc("bill_date").orderByDesc("id");

        Page<SettlementBill> page = settlementBillMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), pageWrapper);
        List<SettlementBillView> views = page.getRecords().stream()
                .map(this::toBillView)
                .toList();

        QueryWrapper<SettlementBill> summaryWrapper = new QueryWrapper<>();
        summaryWrapper.select(
                "COUNT(*) AS bill_count",
                "COALESCE(SUM(gross_amount), 0) AS gross_amount",
                "COALESCE(SUM(merchant_discount), 0) AS merchant_discount",
                "COALESCE(SUM(platform_discount), 0) AS platform_discount",
                "COALESCE(SUM(commission_base), 0) AS commission_base",
                "COALESCE(SUM(commission_amount), 0) AS platform_commission",
                "COALESCE(SUM(performance_fee), 0) AS performance_fee",
                "COALESCE(SUM(refund_amount), 0) AS refund_amount",
                "COALESCE(SUM(payable_amount), 0) AS payable_amount",
                "COALESCE(SUM(CASE WHEN status = 30 THEN payable_amount ELSE 0 END), 0) AS paid_amount",
                "COALESCE(SUM(CASE WHEN status = 30 THEN 1 ELSE 0 END), 0) AS paid_count",
                "COALESCE(SUM(CASE WHEN status IN (0, 10, 20) THEN 1 ELSE 0 END), 0) AS pending_count"
        );
        applyAdminFilters(summaryWrapper, merchantId, status, dateFrom, dateTo);

        Map<String, Object> row = settlementBillMapper.selectMaps(summaryWrapper).stream()
                .findFirst()
                .orElse(Map.of());
        AdminSettlementSummary summary = new AdminSettlementSummary(
                summaryLong(row, "bill_count"),
                summaryLong(row, "gross_amount"),
                summaryLong(row, "merchant_discount"),
                summaryLong(row, "platform_discount"),
                summaryLong(row, "commission_base"),
                summaryLong(row, "platform_commission"),
                summaryLong(row, "performance_fee"),
                summaryLong(row, "refund_amount"),
                summaryLong(row, "payable_amount"),
                summaryLong(row, "paid_amount"),
                summaryLong(row, "paid_count"),
                summaryLong(row, "pending_count")
        );

        return new AdminSettlementStatsResult(views, summary, page.getTotal(),
                normalizedPageNum, normalizedPageSize);
    }

    @Override
    public void auditBill(Long billId, boolean pass, String remark, Long auditorId) {
        if (billId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "billId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        int target = pass ? 10 : 40;
        int rows = settlementBillMapper.update(null, new LambdaUpdateWrapper<SettlementBill>()
                .eq(SettlementBill::getId, billId)
                .eq(SettlementBill::getStatus, 0)
                .set(SettlementBill::getStatus, target)
                .set(SettlementBill::getReviewedBy, auditorId)
                .set(SettlementBill::getReviewedAt, now)
                .set(SettlementBill::getUpdatedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID, "仅待审核的结算单可审核");
        }
        log.info("settlement bill audited billId={} pass={} auditorId={} remark={}",
                billId, pass, auditorId, remark);
    }

    @Override
    public void payBill(Long billId, Long operatorId) {
        if (billId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "billId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = settlementBillMapper.update(null, new LambdaUpdateWrapper<SettlementBill>()
                .eq(SettlementBill::getId, billId)
                .eq(SettlementBill::getStatus, 10)
                .set(SettlementBill::getStatus, 30)
                .set(SettlementBill::getPaidAt, now)
                .set(SettlementBill::getUpdatedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.PAYOUT_STATE_INVALID, "仅审核通过的结算单可打款");
        }
        log.info("settlement bill paid billId={} operatorId={}", billId, operatorId);
    }

    private void applyAdminFilters(QueryWrapper<SettlementBill> wrapper, Long merchantId,
                                   Integer status, LocalDate dateFrom, LocalDate dateTo) {
        wrapper.eq(merchantId != null, "merchant_id", merchantId)
                .eq(status != null, "status", status)
                .ge(dateFrom != null, "bill_date", dateFrom)
                .le(dateTo != null, "bill_date", dateTo);
    }

    private void validateSettlementStatus(Integer status) {
        Set<Integer> valid = Set.of(0, 10, 20, 30, 40);
        if (status != null && !valid.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不合法");
        }
    }

    private long summaryLong(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(alias)) {
                Object value = entry.getValue();
                return value instanceof Number number ? number.longValue() : 0L;
            }
        }
        return 0L;
    }

    private Long generateForMerchant(Long merchantId, LocalDate billDate, List<Order> orders) {
        long grossAmount = 0L;
        long merchantDiscount = 0L;
        long platformDiscount = 0L;
        long commissionBase = 0L;
        long commissionAmount = 0L;
        long payableAmount = 0L;
        List<OrderAmount> amounts = new ArrayList<>();

        for (Order order : orders) {
            long gross = nz(order.getGoodsAmount()) + nz(order.getPackagingFee());
            long merchantDiscountOfOrder = nz(order.getMerchantDiscount());
            long platformDiscountOfOrder = nz(order.getPlatformDiscount());
            long base = gross - merchantDiscountOfOrder;
            long commission = BigDecimal.valueOf(base)
                    .multiply(commissionRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            long merchantAmount = gross - merchantDiscountOfOrder
                    + platformDiscountOfOrder - commission;

            grossAmount += gross;
            merchantDiscount += merchantDiscountOfOrder;
            platformDiscount += platformDiscountOfOrder;
            commissionBase += base;
            commissionAmount += commission;
            payableAmount += merchantAmount;
            amounts.add(new OrderAmount(
                    order, gross, merchantDiscountOfOrder, platformDiscountOfOrder,
                    commission, merchantAmount
            ));
        }

        SettlementBill bill = new SettlementBill();
        bill.setBillNo(generateBillNo());
        bill.setMerchantId(merchantId);
        bill.setBillDate(billDate);
        bill.setPeriodStart(billDate.atStartOfDay());
        bill.setPeriodEnd(billDate.plusDays(1).atStartOfDay());
        bill.setGrossAmount(grossAmount);
        bill.setMerchantDiscount(merchantDiscount);
        bill.setPlatformDiscount(platformDiscount);
        bill.setCommissionBase(commissionBase);
        bill.setCommissionAmount(commissionAmount);
        bill.setPerformanceFee(0L);
        bill.setRefundAmount(0L);
        bill.setCarryForwardAmount(0L);
        bill.setPayableAmount(payableAmount);
        bill.setStatus(0);
        settlementBillMapper.insert(bill);

        for (OrderAmount amount : amounts) {
            SettlementItem goodsItem = new SettlementItem();
            goodsItem.setBillId(bill.getId());
            goodsItem.setOrderId(amount.order().getId());
            goodsItem.setItemType(1);
            goodsItem.setSourceId(amount.order().getId());
            goodsItem.setAmount(amount.gross() - amount.merchantDiscount() + amount.platformDiscount());
            goodsItem.setRemark("goods+packaging-merchantDiscount+platformDiscount");
            settlementItemMapper.insert(goodsItem);

            SettlementItem commissionItem = new SettlementItem();
            commissionItem.setBillId(bill.getId());
            commissionItem.setOrderId(amount.order().getId());
            commissionItem.setItemType(2);
            commissionItem.setSourceId(amount.order().getId());
            commissionItem.setAmount(-amount.commission());
            commissionItem.setRemark("platform commission");
            settlementItemMapper.insert(commissionItem);
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .in(Order::getId, orderIds)
                .eq(Order::getSettlementStatus, 0)
                .set(Order::getSettlementStatus, 1)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (updated != orderIds.size()) {
            throw new BizException(ErrorCode.SETTLEMENT_STATE_INVALID);
        }
        return bill.getId();
    }

    private SettlementBillView toBillView(SettlementBill bill) {
        return new SettlementBillView(
                bill.getId(),
                bill.getBillNo(),
                bill.getMerchantId(),
                bill.getShopId(),
                bill.getBillDate(),
                bill.getGrossAmount(),
                bill.getMerchantDiscount(),
                bill.getPlatformDiscount(),
                bill.getCommissionBase(),
                nz(bill.getCommissionAmount()),
                nz(bill.getPayableAmount()),
                bill.getPerformanceFee(),
                bill.getRefundAmount(),
                bill.getStatus(),
                bill.getCreatedAt()
        );
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private String generateBillNo() {
        String time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SB" + time + random;
    }

    private record OrderAmount(
            Order order,
            long gross,
            long merchantDiscount,
            long platformDiscount,
            long commission,
            long merchantAmount
    ) {
    }
}
