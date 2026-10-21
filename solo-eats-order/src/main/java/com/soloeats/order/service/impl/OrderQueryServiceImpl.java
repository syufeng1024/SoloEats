package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import com.soloeats.order.service.view.AdminOrderSummary;
import com.soloeats.order.service.view.AdminOrderView;
import com.soloeats.order.service.view.MerchantOrderItemView;
import com.soloeats.order.service.view.MerchantOrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final Set<Integer> VALID_STATUSES = Arrays.stream(OrderStatus.values())
            .map(OrderStatus::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public PageResult<MerchantOrderView> pageMerchantOrders(MerchantOrderQuery query) {
        if (query == null || query.merchantId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        validateStatus(query.status());
        long pageNum = normalizePageNum(query.pageNum());
        long pageSize = normalizePageSize(query.pageSize());

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, query.merchantId())
                .eq(query.shopId() != null, Order::getShopId, query.shopId())
                .eq(query.status() != null, Order::getStatus, query.status());
        applyKeyword(wrapper, query.keyword());
        wrapper.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, List<OrderItem>> itemsByOrder = loadItems(page.getRecords());
        List<MerchantOrderView> views = page.getRecords().stream()
                .map(order -> toMerchantView(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return PageResult.of(views, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AdminOrderMonitorResult monitor(AdminOrderQuery query) {
        if (query == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询条件不能为空");
        }
        validateStatus(query.status());
        validateDateRange(query.dateFrom(), query.dateTo());
        long pageNum = normalizePageNum(query.pageNum());
        long pageSize = normalizePageSize(query.pageSize());

        QueryWrapper<Order> pageWrapper = new QueryWrapper<>();
        applyAdminFilters(pageWrapper, query);
        pageWrapper.orderByDesc("created_at").orderByDesc("id");

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), pageWrapper);
        List<AdminOrderView> views = page.getRecords().stream()
                .map(this::toAdminView)
                .toList();

        QueryWrapper<Order> summaryWrapper = new QueryWrapper<>();
        summaryWrapper.select(
                "COUNT(*) AS total",
                "COALESCE(SUM(CASE WHEN status = 10 THEN 1 ELSE 0 END), 0) AS wait_accept",
                "COALESCE(SUM(CASE WHEN status = 20 THEN 1 ELSE 0 END), 0) AS preparing",
                "COALESCE(SUM(CASE WHEN status = 30 THEN 1 ELSE 0 END), 0) AS wait_rider_pickup",
                "COALESCE(SUM(CASE WHEN status = 31 THEN 1 ELSE 0 END), 0) AS wait_user_pickup",
                "COALESCE(SUM(CASE WHEN status = 40 THEN 1 ELSE 0 END), 0) AS delivering",
                "COALESCE(SUM(CASE WHEN status = 60 THEN 1 ELSE 0 END), 0) AS completed",
                "COALESCE(SUM(CASE WHEN status = 70 THEN 1 ELSE 0 END), 0) AS cancelled",
                "COALESCE(SUM(CASE WHEN payment_status <> 0 THEN pay_amount ELSE 0 END), 0) AS paid_amount",
                "COALESCE(SUM(refunded_amount), 0) AS refunded_amount"
        );
        applyAdminFilters(summaryWrapper, query);
        Map<String, Object> row = orderMapper.selectMaps(summaryWrapper).stream()
                .findFirst()
                .orElse(Map.of());

        AdminOrderSummary summary = new AdminOrderSummary(
                summaryLong(row, "total"),
                summaryLong(row, "wait_accept"),
                summaryLong(row, "preparing"),
                summaryLong(row, "wait_rider_pickup"),
                summaryLong(row, "wait_user_pickup"),
                summaryLong(row, "delivering"),
                summaryLong(row, "completed"),
                summaryLong(row, "cancelled"),
                summaryLong(row, "paid_amount"),
                summaryLong(row, "refunded_amount")
        );

        return new AdminOrderMonitorResult(views, page.getTotal(), pageNum, pageSize, summary);
    }

    private void applyAdminFilters(QueryWrapper<Order> wrapper, AdminOrderQuery query) {
        wrapper.eq(query.merchantId() != null, "merchant_id", query.merchantId())
                .eq(query.shopId() != null, "shop_id", query.shopId())
                .eq(query.status() != null, "status", query.status())
                .ge(query.dateFrom() != null, "created_at",
                        query.dateFrom() == null ? null : query.dateFrom().atStartOfDay())
                .lt(query.dateTo() != null, "created_at",
                        query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay());

        String keyword = sanitizeKeyword(query.keyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(inner -> inner.like("order_no", keyword)
                    .or()
                    .like("receiver_phone", keyword)
                    .or()
                    .like("receiver_name", keyword));
        }
    }

    private void applyKeyword(LambdaQueryWrapper<Order> wrapper, String keyword) {
        String sanitized = sanitizeKeyword(keyword);
        if (!StringUtils.hasText(sanitized)) {
            return;
        }
        wrapper.and(inner -> inner.like(Order::getOrderNo, sanitized)
                .or()
                .like(Order::getReceiverPhone, sanitized)
                .or()
                .like(Order::getReceiverName, sanitized));
    }

    private Map<Long, List<OrderItem>> loadItems(List<Order> orders) {
        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private MerchantOrderView toMerchantView(Order order, List<OrderItem> items) {
        List<MerchantOrderItemView> itemViews = items.stream()
                .map(item -> new MerchantOrderItemView(
                        item.getSkuId(),
                        item.getSkuName(),
                        item.getQuantity(),
                        item.getOriginalUnitPrice(),
                        item.getPayableAmount()
                ))
                .toList();
        return new MerchantOrderView(
                order.getId(),
                order.getOrderNo(),
                order.getMerchantId(),
                order.getShopId(),
                order.getStatus(),
                statusName(order.getStatus()),
                order.getFulfillmentType(),
                order.getUserId(),
                order.getGoodsAmount(),
                order.getPackagingFee(),
                order.getDeliveryFee(),
                order.getPlatformDiscount(),
                order.getMerchantDiscount(),
                order.getPayAmount(),
                order.getRefundedAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getRemark(),
                order.getPickupCode(),
                order.getCreatedAt(),
                order.getExpectedDeliveryAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                itemViews
        );
    }

    private AdminOrderView toAdminView(Order order) {
        return new AdminOrderView(
                order.getId(),
                order.getOrderNo(),
                order.getMerchantId(),
                order.getShopId(),
                order.getUserId(),
                order.getStatus(),
                statusName(order.getStatus()),
                order.getFulfillmentType(),
                order.getPaymentStatus(),
                order.getRefundStatus(),
                order.getSettlementStatus(),
                order.getPayAmount(),
                order.getRefundedAmount(),
                order.getReceiverName(),
                maskPhone(order.getReceiverPhone()),
                order.getReceiverAddress(),
                order.getCreatedAt(),
                order.getExpectedDeliveryAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancelReason()
        );
    }

    private void validateStatus(Integer status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不合法");
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 关键字按字面量匹配，避免用户输入 % / _ 改变 LIKE 语义
        return keyword.trim().replace("\\", "").replace("%", "").replace("_", "");
    }

    private String statusName(Integer code) {
        if (code == null) {
            return "未知状态";
        }
        return Arrays.stream(OrderStatus.values())
                .filter(status -> status.getCode() == code)
                .map(OrderStatus::getDescription)
                .findFirst()
                .orElse("未知状态");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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
}
