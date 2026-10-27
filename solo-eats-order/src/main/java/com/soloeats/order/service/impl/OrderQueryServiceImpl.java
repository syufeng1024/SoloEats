package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.OrderStatusLog;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.OrderQueryService;
import com.soloeats.order.service.query.AdminOrderQuery;
import com.soloeats.order.service.query.MerchantOrderQuery;
import com.soloeats.order.service.view.AdminOrderMonitorResult;
import com.soloeats.order.service.view.AdminOrderSummary;
import com.soloeats.order.service.view.AdminOrderView;
import com.soloeats.order.service.view.MerchantDashboardView;
import com.soloeats.order.service.view.MerchantOrderDetailView;
import com.soloeats.order.service.view.MerchantOrderItemView;
import com.soloeats.order.service.view.MerchantOrderView;
import com.soloeats.order.service.view.OrderItemBriefView;
import com.soloeats.order.service.view.OrderTimelineView;
import com.soloeats.order.service.view.UserOrderDetailView;
import com.soloeats.order.service.view.UserOrderItemView;
import com.soloeats.order.service.view.UserOrderView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryServiceImpl implements OrderQueryService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int USER_DEFAULT_PAGE_NUM = 1;
    private static final int USER_DEFAULT_PAGE_SIZE = 10;
    private static final int USER_MAX_PAGE_SIZE = 50;
    /** {@code t_order.refund_status}：0 无退款 / 1 部分退款 / 2 全额退款。 */
    private static final int ORDER_REFUND_STATUS_FULL = 2;
    private static final Set<Integer> VALID_STATUSES = Arrays.stream(OrderStatus.values())
            .map(OrderStatus::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final DeliveryService deliveryService;

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
    public boolean belongsToUser(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            return false;
        }
        // t_order 无逻辑删除列，selectById 查不到即视为不存在（含已删除）
        Order order = orderMapper.selectById(orderId);
        return order != null && Objects.equals(order.getUserId(), userId);
    }

    @Override
    public PageResult<UserOrderView> pageUserOrders(Long userId, Integer status,
                                                    int pageNum, int pageSize) {
        if (userId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 不能为空");
        }
        validateStatus(status);
        long normalizedPageNum = normalizeUserPageNum(pageNum);
        long normalizedPageSize = normalizeUserPageSize(pageSize);

        // t_order 无逻辑删除列，不做 deleted 过滤
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);

        Page<Order> page = orderMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        Map<Long, Integer> itemCounts = countItems(page.getRecords());
        List<UserOrderView> views = page.getRecords().stream()
                .map(order -> toUserListView(order, itemCounts.getOrDefault(order.getId(), 0)))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public UserOrderDetailView getUserOrder(Long userId, Long orderId) {
        if (userId == null || orderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 与 orderId 不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderId, orderId)
                        .orderByAsc(OrderStatusLog::getCreatedAt)
                        .orderByAsc(OrderStatusLog::getId));

        DeliveryOrder delivery = loadDeliveryQuietly(orderId);

        List<UserOrderItemView> itemViews = items.stream()
                .map(this::toUserItemView)
                .toList();
        List<OrderTimelineView> timeline = logs.stream()
                .map(this::toTimelineView)
                .toList();
        int itemCount = items.stream()
                .map(OrderItem::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return new UserOrderDetailView(
                order.getId(),
                order.getOrderNo(),
                order.getShopId(),
                order.getStatus(),
                statusName(order.getStatus(), order.getRefundStatus()),
                order.getFulfillmentType(),
                fulfillmentTypeName(order.getFulfillmentType()),
                order.getGoodsAmount(),
                order.getPackagingFee(),
                order.getDeliveryFee(),
                order.getPayAmount(),
                itemCount,
                order.getPickupCode(),
                order.getCreatedAt(),
                order.getReceiverName(),
                maskPhone(order.getReceiverPhone()),
                order.getReceiverAddress(),
                order.getRemark(),
                order.getRefundStatus(),
                order.getRefundedAmount(),
                delivery == null ? null : delivery.getId(),
                delivery == null ? null : delivery.getStatus(),
                itemViews,
                timeline
        );
    }

    @Override
    public MerchantOrderDetailView getMerchantOrder(Long orderId) {
        if (orderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderId, orderId)
                        .orderByAsc(OrderStatusLog::getCreatedAt)
                        .orderByAsc(OrderStatusLog::getId));

        List<UserOrderItemView> itemViews = items.stream()
                .map(this::toUserItemView)
                .toList();
        List<OrderTimelineView> timeline = logs.stream()
                .map(this::toTimelineView)
                .toList();

        return new MerchantOrderDetailView(
                order.getId(),
                order.getOrderNo(),
                order.getShopId(),
                order.getStatus(),
                statusName(order.getStatus(), order.getRefundStatus()),
                order.getFulfillmentType(),
                fulfillmentTypeName(order.getFulfillmentType()),
                order.getGoodsAmount(),
                order.getPackagingFee(),
                order.getDeliveryFee(),
                order.getPayAmount(),
                order.getReceiverName(),
                maskPhone(order.getReceiverPhone()),
                order.getReceiverAddress(),
                order.getRemark(),
                order.getPickupCode(),
                order.getPickupReadyAt(),
                order.getCreatedAt(),
                // t_order 无 paid_at 列，按约定返回 null，不新增列
                null,
                itemViews,
                timeline
        );
    }

    @Override
    public MerchantDashboardView dashboard(Long merchantId, Long shopId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> row = orderMapper.selectMerchantDashboard(merchantId, shopId, dayStart);
        if (row == null) {
            row = Map.of();
        }
        return new MerchantDashboardView(
                summaryLong(row, "today_order_count"),
                summaryLong(row, "pending_accept_count"),
                summaryLong(row, "preparing_count"),
                summaryLong(row, "wait_pickup_count"),
                summaryLong(row, "completed_today_count")
        );
    }

    @Override
    public List<OrderItemBriefView> listItemsByOrderId(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .map(item -> new OrderItemBriefView(
                        item.getSkuId(),
                        item.getSkuName(),
                        item.getQuantity(),
                        item.getOriginalUnitPrice(),
                        item.getPayableAmount()))
                .toList();
    }

    @Override
    public Map<Long, Integer> itemCountByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = orderIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("order_id", "COALESCE(SUM(quantity), 0) AS item_count")
                .in("order_id", distinctIds)
                .groupBy("order_id");
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : orderItemMapper.selectMaps(wrapper)) {
            long orderId = summaryLong(row, "order_id");
            if (orderId > 0L) {
                counts.put(orderId, (int) summaryLong(row, "item_count"));
            }
        }
        return counts;
    }

    private Map<Long, Integer> countItems(List<Order> orders) {
        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        // 批量 SUM，避免逐单查询（N+1）
        QueryWrapper<OrderItem> wrapper = new QueryWrapper<>();
        wrapper.select("order_id", "COALESCE(SUM(quantity), 0) AS item_count")
                .in("order_id", orderIds)
                .groupBy("order_id");
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : orderItemMapper.selectMaps(wrapper)) {
            long orderId = summaryLong(row, "order_id");
            if (orderId > 0L) {
                counts.put(orderId, (int) summaryLong(row, "item_count"));
            }
        }
        return counts;
    }

    private UserOrderView toUserListView(Order order, int itemCount) {
        return new UserOrderView(
                order.getId(),
                order.getOrderNo(),
                order.getShopId(),
                order.getStatus(),
                statusName(order.getStatus(), order.getRefundStatus()),
                order.getFulfillmentType(),
                fulfillmentTypeName(order.getFulfillmentType()),
                order.getGoodsAmount(),
                order.getPackagingFee(),
                order.getDeliveryFee(),
                order.getPayAmount(),
                itemCount,
                order.getPickupCode(),
                order.getCreatedAt()
        );
    }

    /**
     * 配送单是订单详情的附加信息：无单或查询异常都只降级为 null，不影响详情主流程。
     */
    private DeliveryOrder loadDeliveryQuietly(Long orderId) {
        try {
            return deliveryService.getByOrderId(orderId);
        } catch (Exception ex) {
            log.warn("查询配送单失败，订单详情降级为无配送单, orderId={}", orderId, ex);
            return null;
        }
    }

    private UserOrderItemView toUserItemView(OrderItem item) {
        return new UserOrderItemView(
                item.getId(),
                item.getSkuId(),
                item.getSkuName(),
                item.getQuantity(),
                item.getOriginalUnitPrice(),
                item.getOriginalAmount(),
                item.getPayableAmount()
        );
    }

    private OrderTimelineView toTimelineView(OrderStatusLog log) {
        return new OrderTimelineView(
                log.getFromStatus(),
                statusName(log.getFromStatus()),
                log.getToStatus(),
                statusName(log.getToStatus()),
                log.getEvent(),
                log.getOperatorType(),
                log.getCreatedAt()
        );
    }

    private String fulfillmentTypeName(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "配送";
            case 2 -> "自取";
            default -> "未知";
        };
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
                // 退款语义由 refund_status 承载：status=70 同时表示「用户取消」与「已退款」，
                // 运营需要能单独筛出「已退款」单据（技术架构 3.2 未新增状态码）
                .eq(query.refundStatus() != null, "refund_status", query.refundStatus())
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
                statusName(order.getStatus(), order.getRefundStatus()),
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
                statusName(order.getStatus(), order.getRefundStatus()),
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

    private long normalizeUserPageNum(int pageNum) {
        return pageNum < 1 ? USER_DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizeUserPageSize(int pageSize) {
        if (pageSize < 1) {
            return USER_DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, USER_MAX_PAGE_SIZE);
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

    /**
     * 对外展示的状态文案：退款终态语义由 {@code t_order.refund_status} 承载。
     *
     * <p>订单状态机不设「已退款」状态（技术架构 3.2 的流转表与
     * {@code OrderStateMachineTest} 均锁定 {@code REFUND_FULL_SUCCESS → CANCELLED}），
     * 全额退款后订单仍是「已取消」。若直接下发「已取消」，用户端会把「商家拒单退款」
     * 看成「自己取消的订单」，客服也无法按状态区分。
     *
     * <p>因此展示层做一次纠正：{@code refund_status=2（全额退款）} 且订单处于退款可达的终态
     * （「已取消」= 未完成即退款、「已完成」= 售后期内退款）时，文案统一为「已退款」。
     * 这不改变任何状态流转，只影响展示；退款语义的唯一事实来源是 {@code refund_status}。
     */
    private String statusName(Integer code, Integer refundStatus) {
        if (Objects.equals(refundStatus, ORDER_REFUND_STATUS_FULL)
                && (Objects.equals(code, OrderStatus.CANCELLED.getCode())
                || Objects.equals(code, OrderStatus.COMPLETED.getCode()))) {
            return "已退款";
        }
        return statusName(code);
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
