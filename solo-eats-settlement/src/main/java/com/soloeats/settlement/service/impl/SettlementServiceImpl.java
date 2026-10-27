package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.settlement.domain.entity.SettlementBill;
import com.soloeats.settlement.domain.entity.SettlementItem;
import com.soloeats.settlement.domain.enums.FlowType;
import com.soloeats.settlement.mapper.SettlementBillMapper;
import com.soloeats.settlement.mapper.SettlementItemMapper;
import com.soloeats.settlement.service.CommissionService;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.AdminSettlementStatsResult;
import com.soloeats.settlement.service.view.AdminSettlementSummary;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import com.soloeats.settlement.service.view.SettlementBillView;
import com.soloeats.settlement.service.view.SettlementDetailResult;
import com.soloeats.settlement.service.view.SettlementGenerateResult;
import com.soloeats.settlement.service.view.SettlementItemView;
import com.soloeats.settlement.service.view.SettlementListResult;
import com.soloeats.settlement.service.view.SettlementOrderView;
import com.soloeats.settlement.service.view.SettlementSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    /** 三级抽佣规则引擎：费率来源的唯一入口，未命中时由引擎回落默认费率。 */
    private final CommissionService commissionService;

    /** 平台资金流水：结算单打款成功后记结算支出。 */
    private final FundFlowService fundFlowService;

    @Override
    public SettlementGenerateResult generateDailyBills() {
        LocalDate billDate = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = billDate.atStartOfDay();
        LocalDateTime periodEnd = billDate.plusDays(1).atStartOfDay();

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .eq(Order::getSettlementStatus, 0)
                .eq(Order::getRefundStatus, 0)
                .eq(Order::getRefundedAmount, 0L)
                .isNotNull(Order::getSettleEligibleAt)
                .le(Order::getSettleEligibleAt, now)
                .orderByAsc(Order::getId));

        Map<Long, List<Order>> ordersByMerchant = orders.stream()
                .filter(order -> order.getMerchantId() != null)
                .collect(Collectors.groupingBy(
                        Order::getMerchantId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, List<RefundOrder>> refundsByMerchant = loadRefundsForSettlement(periodStart, periodEnd);

        // 商家集合取并集：某商家本期可能只有退款冲抵（无新订单），仍须出调整账单
        Set<Long> merchantIds = new LinkedHashSet<>(ordersByMerchant.keySet());
        merchantIds.addAll(refundsByMerchant.keySet());

        List<Long> billIds = new ArrayList<>();
        int orderCount = 0;
        for (Long merchantId : merchantIds) {
            if (settlementBillMapper.findByMerchantAndDate(merchantId, billDate) != null) {
                continue;
            }
            List<Order> merchantOrders = ordersByMerchant.getOrDefault(merchantId, List.of());
            List<RefundOrder> merchantRefunds = refundsByMerchant.getOrDefault(merchantId, List.of());
            try {
                Long billId = transactionTemplate.execute(status ->
                        generateForMerchant(merchantId, billDate, merchantOrders, merchantRefunds));
                if (billId != null) {
                    billIds.add(billId);
                    orderCount += merchantOrders.size();
                }
            } catch (DuplicateKeyException ex) {
                log.warn("settlement bill already exists merchant={} date={}", merchantId, billDate);
            }
        }
        return new SettlementGenerateResult(billIds.size(), orderCount, billIds);
    }

    /**
     * 载入本期需冲抵的退款：成功退款 + 已完成结算的订单（技术架构 3.10.4）。
     *
     * <p>未结算就退款的订单本就不会进入账单（{@code refund_status=0} 过滤），无需冲抵；
     * 只有「已经结算拿过钱、之后才退款」的订单才需要在本期出负项回收资金。
     */
    private Map<Long, List<RefundOrder>> loadRefundsForSettlement(LocalDateTime periodStart,
                                                                  LocalDateTime periodEnd) {
        List<RefundOrder> refunds = settlementBillMapper.findSettleableRefunds(
                RefundStatus.SUCCESS.getCode(), periodStart, periodEnd);
        if (refunds == null || refunds.isEmpty()) {
            return Map.of();
        }
        Set<Long> orderIds = refunds.stream()
                .map(RefundOrder::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Order> orderById = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, order -> order, (a, b) -> a));

        Map<Long, List<RefundOrder>> byMerchant = new LinkedHashMap<>();
        for (RefundOrder refund : refunds) {
            Order order = orderById.get(refund.getOrderId());
            if (order == null || order.getMerchantId() == null) {
                continue;
            }
            byMerchant.computeIfAbsent(order.getMerchantId(), key -> new ArrayList<>()).add(refund);
        }
        return byMerchant;
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
        recordSettlementExpense(billId);
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

    /**
     * 生成单个商家的账单。
     *
     * <p>金额口径严格按《技术架构与数据库设计说明书》3.10.2：
     * <pre>
     * payable = gross_amount - merchant_discount + platform_discount
     *           - commission_amount - performance_fee - refund_amount
     * </pre>
     * 其中 {@code refund_amount} 为本期需冲抵的已结算订单退款（3.10.4），
     * 另承接上期 {@code carry_forward_amount}；本期应结为负时不扣款，转为下期负项。
     *
     * @param orders  本期可入账订单（已过售后期、未结算、未退款）
     * @param refunds 本期需冲抵的退款（所属订单已完成结算）
     */
    private Long generateForMerchant(Long merchantId, LocalDate billDate,
                                     List<Order> orders, List<RefundOrder> refunds) {
        if (orders.isEmpty() && refunds.isEmpty()) {
            return null;
        }
        long grossAmount = 0L;
        long merchantDiscount = 0L;
        long platformDiscount = 0L;
        long commissionBase = 0L;
        long commissionAmount = 0L;
        long orderPayable = 0L;
        List<OrderAmount> amounts = new ArrayList<>();
        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        for (Order order : orders) {
            long gross = nz(order.getGoodsAmount()) + nz(order.getPackagingFee());
            long merchantDiscountOfOrder = nz(order.getMerchantDiscount());
            long platformDiscountOfOrder = nz(order.getPlatformDiscount());
            // 抽佣基数：商品金额 − 商家承担优惠（不含打包费）；打包费仍全额计入结算营业额，不参与抽佣
            long base = nz(order.getGoodsAmount()) - merchantDiscountOfOrder;
            // 费率来源改为三级规则引擎（活动 > 商家 > 品类 > 全局），无命中时引擎内部回落默认费率
            CommissionResolveResult commissionResult = resolveCommission(order, base);
            long commission = commissionResult.commissionAmount();
            long merchantAmount = gross - merchantDiscountOfOrder
                    + platformDiscountOfOrder - commission;

            grossAmount += gross;
            merchantDiscount += merchantDiscountOfOrder;
            platformDiscount += platformDiscountOfOrder;
            commissionBase += base;
            commissionAmount += commission;
            orderPayable += merchantAmount;
            amounts.add(new OrderAmount(
                    order, gross, merchantDiscountOfOrder, platformDiscountOfOrder,
                    commission, merchantAmount
            ));
        }

        // 退款冲抵（3.10.4）：按该订单原本实结给商家的金额回收，保证全额退款后商家该单净额为 0。
        // 幂等：item_type=4 + source_id=退款单ID 已存在则跳过，避免同一笔退款被多期重复冲抵。
        long refundAmount = 0L;
        List<RefundClawback> clawbacks = new ArrayList<>();
        for (RefundOrder refund : refunds) {
            if (settlementItemMapper.countByTypeAndSource(4, refund.getId()) > 0) {
                continue;
            }
            long originalSettle = settlementItemMapper.sumOrderSettleAmount(refund.getOrderId());
            if (originalSettle <= 0L) {
                // 该订单从未出现在任何账单中，没有可回收的金额
                continue;
            }
            refundAmount += originalSettle;
            clawbacks.add(new RefundClawback(refund, originalSettle));
        }

        // 承接上期负项（3.10.2）：账单生成后不可修改，故以 item_type=5 + source_id=上期账单ID 判定是否已承接
        long carryForwardIn = 0L;
        SettlementBill previous = settlementBillMapper.findLatestCarryForward(merchantId);
        if (previous != null
                && previous.getCarryForwardAmount() != null
                && previous.getCarryForwardAmount() < 0L
                && settlementItemMapper.countByTypeAndSource(5, previous.getId()) == 0) {
            carryForwardIn = previous.getCarryForwardAmount();
        }

        long payableAmount = orderPayable - refundAmount + carryForwardIn;
        long carryForwardOut = 0L;
        if (payableAmount < 0L) {
            // 本期应结为负：不在本期扣款，转入下期负项
            carryForwardOut = payableAmount;
            payableAmount = 0L;
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
        bill.setRefundAmount(refundAmount);
        bill.setCarryForwardAmount(carryForwardOut);
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

        for (RefundClawback clawback : clawbacks) {
            SettlementItem refundItem = new SettlementItem();
            refundItem.setBillId(bill.getId());
            refundItem.setOrderId(clawback.refund().getOrderId());
            refundItem.setItemType(4);
            refundItem.setSourceId(clawback.refund().getId());
            refundItem.setAmount(-clawback.originalSettle());
            refundItem.setRemark("refund clawback refundNo=" + clawback.refund().getRefundNo());
            settlementItemMapper.insert(refundItem);
        }

        if (carryForwardIn != 0L) {
            SettlementItem carryItem = new SettlementItem();
            carryItem.setBillId(bill.getId());
            carryItem.setItemType(5);
            carryItem.setSourceId(previous.getId());
            carryItem.setAmount(carryForwardIn);
            carryItem.setRemark("carry forward from " + previous.getBillNo());
            settlementItemMapper.insert(carryItem);
        }

        // 抽佣明细回溯账单：账单落库后回填 settlement_bill_id，便于「账单 → 抽佣明细」核对
        if (!orderIds.isEmpty()) {
            bindCommissionRecords(bill.getId(), orderIds);

            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .in(Order::getId, orderIds)
                    .eq(Order::getSettlementStatus, 0)
                    .set(Order::getSettlementStatus, 1)
                    .set(Order::getUpdatedAt, LocalDateTime.now()));
            if (updated != orderIds.size()) {
                throw new BizException(ErrorCode.SETTLEMENT_STATE_INVALID);
            }
        }
        return bill.getId();
    }

    /**
     * 抽佣费率来源：三级规则引擎（活动 > 商家 > 品类 > 全局默认），
     * 引擎内部在无规则命中时回落 {@code app.settlement.default-commission-rate}（默认 0.0600），
     * 并把 rule_id / rule_scope 记 NULL。抽佣金额只在这里算一次，落库后不再重算。
     */
    private CommissionResolveResult resolveCommission(Order order, long base) {
        return commissionService.resolveAndRecord(order, base);
    }

    /**
     * 抽佣明细回填结算单归属，便于「账单 → 抽佣明细」溯源核对。
     */
    private void bindCommissionRecords(Long billId, List<Long> orderIds) {
        int bound = commissionService.bindSettlementBill(billId, orderIds);
        log.debug("commission records bound billId={} rows={}", billId, bound);
    }

    /**
     * 打款成功后记一条结算支出流水（flow_type = 2，direction = 2 出）。
     *
     * <p>取舍：流水与打款 <b>不在同一事务</b>。打款状态已通过条件更新落库，若此处失败只记
     * error 日志、不回滚打款，避免「钱已经打了但事务回滚」的资损；流水侧靠
     * {@code uk_biz(flow_type, biz_type, biz_id)} 幂等，补偿重试也不会重复记账。
     */
    private void recordSettlementExpense(Long billId) {
        SettlementBill bill = settlementBillMapper.selectById(billId);
        if (bill == null) {
            // 数据一致性兜底：条件更新成功即代表账单存在，查不到只可能是脏数据；
            // 打款已落库，此时抛异常会让调用方误以为可回滚，故只记告警。
            log.warn("settlement bill missing after pay billId={}", billId);
            return;
        }
        try {
            fundFlowService.record(
                    FlowType.SETTLEMENT_EXPENSE.getCode(),
                    FundFlowService.BIZ_TYPE_SETTLEMENT_BILL,
                    billId,
                    FlowType.SETTLEMENT_EXPENSE.getDefaultDirection(),
                    nz(bill.getPayableAmount()),
                    "结算单 " + bill.getBillNo() + " 打款支出");
        } catch (RuntimeException ex) {
            log.error("settlement expense fund flow failed billId={}", billId, ex);
        }
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

    /**
     * 一笔待冲抵的退款及其冲抵金额。
     *
     * @param refund        成功退款单
     * @param originalSettle 该订单原本实结给商家的金额（商品项 + 佣金项），冲抵时取负值
     */
    private record RefundClawback(RefundOrder refund, long originalSettle) {
    }
}
