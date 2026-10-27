package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.OrderStatusLog;
import com.soloeats.order.domain.entity.RefundItem;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.mapper.RefundItemMapper;
import com.soloeats.order.mapper.RefundOrderMapper;
import com.soloeats.order.service.AdminOrderReadService;
import com.soloeats.order.service.query.AdminExceptionQuery;
import com.soloeats.order.service.query.AdminRefundQuery;
import com.soloeats.order.service.view.AdminExceptionOrderView;
import com.soloeats.order.service.view.AdminOrderDetailItemView;
import com.soloeats.order.service.view.AdminOrderDetailRefundView;
import com.soloeats.order.service.view.AdminOrderDetailView;
import com.soloeats.order.service.view.AdminRefundDetailView;
import com.soloeats.order.service.view.AdminRefundItemView;
import com.soloeats.order.service.view.AdminRefundView;
import com.soloeats.order.service.view.OrderTimelineView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运营后台订单中心只读查询。所有方法只做 SELECT，不触碰订单状态机。
 */
@Service
@RequiredArgsConstructor
public class AdminOrderReadServiceImpl implements AdminOrderReadService {

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    /** t_refund_order.status：0申请 10商家同意 11商家拒绝 12平台仲裁 20退款中 30成功 31失败 40取消 50自动同意。 */
    private static final Set<Integer> VALID_REFUND_STATUSES =
            Set.of(0, 10, 11, 12, 20, 30, 31, 40, 50);
    /** t_order.exception_type：1无骑手 2联系不上 3地址异常 4餐品破损 5商家超时 6自取超时 7其他。 */
    private static final Set<Integer> VALID_EXCEPTION_TYPES = Set.of(1, 2, 3, 4, 5, 6, 7);
    private static final Set<Integer> VALID_ORDER_STATUSES = Arrays.stream(OrderStatus.values())
            .map(OrderStatus::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final RefundItemMapper refundItemMapper;

    @Override
    public AdminOrderDetailView getOrderDetail(Long orderId) {
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
        RefundOrder latestRefund = refundOrderMapper.findLatestByOrderId(orderId);

        return new AdminOrderDetailView(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getMerchantId(),
                order.getShopId(),
                order.getStatus(),
                statusName(order.getStatus(), order.getRefundStatus()),
                order.getFulfillmentType(),
                fulfillmentTypeName(order.getFulfillmentType()),
                order.getPaymentStatus(),
                order.getRefundStatus(),
                order.getSettlementStatus(),
                order.getExceptionType(),
                exceptionTypeName(order.getExceptionType()),
                order.getGoodsAmount(),
                order.getPackagingFee(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                order.getPlatformDiscount(),
                order.getMerchantDiscount(),
                order.getPayAmount(),
                order.getRefundedAmount(),
                order.getReceiverName(),
                maskPhone(order.getReceiverPhone()),
                order.getReceiverAddress(),
                order.getRemark(),
                maskPhone(order.getPickupPhone()),
                order.getPickupCode(),
                order.getPickupReadyAt(),
                order.getPickedUpAt(),
                order.getExpectedDeliveryAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items.stream().map(this::toItemView).toList(),
                latestRefund == null ? null : toDetailRefundView(latestRefund),
                logs.stream().map(this::toTimelineView).toList()
        );
    }

    @Override
    public PageResult<AdminRefundView> pageRefunds(AdminRefundQuery query) {
        if (query == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询条件不能为空");
        }
        validateRefundStatus(query.status());
        validateDateRange(query.dateFrom(), query.dateTo());
        long pageNum = normalizePageNum(query.pageNum());
        long pageSize = normalizePageSize(query.pageSize());

        QueryWrapper<RefundOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(query.status() != null, "status", query.status())
                .ge(query.dateFrom() != null, "created_at",
                        query.dateFrom() == null ? null : query.dateFrom().atStartOfDay())
                .lt(query.dateTo() != null, "created_at",
                        query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay())
                .orderByDesc("created_at")
                .orderByDesc("id");

        String keyword = sanitizeKeyword(query.keyword());
        if (StringUtils.hasText(keyword)) {
            // 子查询参数化，避免把关键字拼进 SQL；同时匹配退款单号与关联订单号
            wrapper.and(inner -> inner.like("refund_no", keyword)
                    .or()
                    .apply("EXISTS (SELECT 1 FROM t_order o"
                                    + " WHERE o.id = t_refund_order.order_id"
                                    + " AND o.order_no LIKE {0})",
                            "%" + keyword + "%"));
        }

        Page<RefundOrder> page = refundOrderMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        Map<Long, Order> ordersById = loadOrders(page.getRecords());
        List<AdminRefundView> views = page.getRecords().stream()
                .map(refund -> toRefundView(refund,
                        ordersById.get(refund.getOrderId())))
                .toList();
        return PageResult.of(views, page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AdminRefundDetailView getRefundDetail(Long refundId) {
        if (refundId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "refundId 不能为空");
        }
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null) {
            throw new BizException(ErrorCode.REFUND_NOT_FOUND);
        }
        Order order = refund.getOrderId() == null
                ? null
                : orderMapper.selectById(refund.getOrderId());
        List<RefundItem> items = refundItemMapper.selectList(
                new LambdaQueryWrapper<RefundItem>()
                        .eq(RefundItem::getRefundId, refundId)
                        .orderByAsc(RefundItem::getId));
        return new AdminRefundDetailView(
                toRefundView(refund, order),
                items.stream().map(this::toRefundItemView).toList()
        );
    }

    @Override
    public PageResult<AdminExceptionOrderView> pageExceptionOrders(AdminExceptionQuery query) {
        if (query == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询条件不能为空");
        }
        validateExceptionType(query.exceptionType());
        validateOrderStatus(query.status());
        validateDateRange(query.dateFrom(), query.dateTo());
        long pageNum = normalizePageNum(query.pageNum());
        long pageSize = normalizePageSize(query.pageSize());

        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.ne("exception_type", 0)
                .eq(query.exceptionType() != null, "exception_type", query.exceptionType())
                .eq(query.status() != null, "status", query.status())
                .eq(query.merchantId() != null, "merchant_id", query.merchantId())
                .eq(query.shopId() != null, "shop_id", query.shopId())
                .ge(query.dateFrom() != null, "created_at",
                        query.dateFrom() == null ? null : query.dateFrom().atStartOfDay())
                .lt(query.dateTo() != null, "created_at",
                        query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay())
                .orderByDesc("created_at")
                .orderByDesc("id");

        String keyword = sanitizeKeyword(query.keyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(inner -> inner.like("order_no", keyword)
                    .or()
                    .like("receiver_phone", keyword)
                    .or()
                    .like("receiver_name", keyword));
        }

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AdminExceptionOrderView> views = page.getRecords().stream()
                .map(order -> new AdminExceptionOrderView(
                        order.getId(),
                        order.getOrderNo(),
                        order.getMerchantId(),
                        order.getShopId(),
                        order.getStatus(),
                        statusName(order.getStatus(), order.getRefundStatus()),
                        order.getExceptionType(),
                        exceptionTypeName(order.getExceptionType()),
                        order.getPayAmount(),
                        order.getCreatedAt()))
                .toList();
        return PageResult.of(views, page.getTotal(), pageNum, pageSize);
    }

    private Map<Long, Order> loadOrders(List<RefundOrder> refunds) {
        List<Long> orderIds = refunds.stream()
                .map(RefundOrder::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity(), (left, right) -> left));
    }

    private AdminOrderDetailItemView toItemView(OrderItem item) {
        return new AdminOrderDetailItemView(
                item.getId(),
                item.getSkuId(),
                item.getSkuName(),
                item.getSpecJson(),
                item.getImageUrl(),
                item.getOriginalUnitPrice(),
                item.getQuantity(),
                item.getOriginalAmount(),
                item.getPayableAmount(),
                item.getRefundedQty(),
                item.getRefundedAmount()
        );
    }

    private AdminOrderDetailRefundView toDetailRefundView(RefundOrder refund) {
        return new AdminOrderDetailRefundView(
                refund.getId(),
                refund.getRefundNo(),
                refund.getRefundType(),
                refundTypeName(refund.getRefundType()),
                refund.getAmount(),
                refund.getStatus(),
                refundStatusName(refund.getStatus()),
                refund.getReason(),
                refund.getMerchantReviewAt(),
                refund.getSuccessAt(),
                refund.getCreatedAt()
        );
    }

    private AdminRefundView toRefundView(RefundOrder refund, Order order) {
        return new AdminRefundView(
                refund.getId(),
                refund.getRefundNo(),
                refund.getOrderId(),
                order == null ? null : order.getOrderNo(),
                order == null ? null : order.getStatus(),
                order == null ? null : statusName(order.getStatus(), order.getRefundStatus()),
                refund.getRefundType(),
                refundTypeName(refund.getRefundType()),
                refund.getAmount(),
                refund.getStatus(),
                refundStatusName(refund.getStatus()),
                refund.getReason(),
                refund.getMerchantReviewAt(),
                refund.getSuccessAt(),
                refund.getCreatedAt()
        );
    }

    private AdminRefundItemView toRefundItemView(RefundItem item) {
        return new AdminRefundItemView(
                item.getId(),
                item.getOrderItemId(),
                item.getSkuId(),
                item.getQty(),
                item.getRefundAmount(),
                item.getRestockFlag(),
                item.getCreatedAt()
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

    private void validateOrderStatus(Integer status) {
        if (status != null && !VALID_ORDER_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 不合法");
        }
    }

    private void validateRefundStatus(Integer status) {
        if (status != null && !VALID_REFUND_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退款状态不合法");
        }
    }

    private void validateExceptionType(Integer exceptionType) {
        if (exceptionType != null && !VALID_EXCEPTION_TYPES.contains(exceptionType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "异常类型不合法");
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
            return null;
        }
        return Arrays.stream(OrderStatus.values())
                .filter(status -> status.getCode() == code)
                .map(OrderStatus::getDescription)
                .findFirst()
                .orElse("未知状态");
    }

    /**
     * 后台展示文案：全额退款的订单虽落「已取消」，但对运营应显示「已退款」，
     * 否则财务/客服在订单列表里无法区分「用户主动取消」与「已退款」。
     *
     * <p>只影响展示，不改变状态流转（状态机不设「已退款」状态）。
     */
    private String statusName(Integer code, Integer refundStatus) {
        if (Objects.equals(refundStatus, 2)
                && (Objects.equals(code, OrderStatus.CANCELLED.getCode())
                || Objects.equals(code, OrderStatus.COMPLETED.getCode()))) {
            return "已退款";
        }
        return statusName(code);
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

    private String refundTypeName(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "全额退款";
            case 2 -> "部分退款";
            case 3 -> "迟到支付退款";
            default -> "未知类型";
        };
    }

    private String refundStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "申请中";
            case 10 -> "商家同意";
            case 11 -> "商家拒绝";
            case 12 -> "平台仲裁";
            case 20 -> "退款中";
            case 30 -> "退款成功";
            case 31 -> "退款失败";
            case 40 -> "已取消";
            case 50 -> "自动同意";
            default -> "未知状态";
        };
    }

    private String exceptionTypeName(Integer type) {
        if (type == null || type == 0) {
            return null;
        }
        return switch (type) {
            case 1 -> "无骑手";
            case 2 -> "联系不上";
            case 3 -> "地址异常";
            case 4 -> "餐品破损";
            case 5 -> "商家超时";
            case 6 -> "自取超时";
            case 7 -> "其他";
            default -> "未知类型";
        };
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
