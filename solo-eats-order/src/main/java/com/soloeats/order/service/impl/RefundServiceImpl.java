package com.soloeats.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.common.payment.PaymentGateway;
import com.soloeats.common.payment.PaymentInfo;
import com.soloeats.common.payment.RefundGatewayResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderDiscount;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.RefundItem;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.enums.RefundStatus;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.RefundItemMapper;
import com.soloeats.order.mapper.RefundOrderMapper;
import com.soloeats.order.service.OrderApplicationService;
import com.soloeats.order.service.RefundService;
import com.soloeats.order.service.result.CancelResult;
import com.soloeats.order.service.result.RefundResult;
import com.soloeats.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDiscountMapper orderDiscountMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final RefundItemMapper refundItemMapper;
    private final OrderApplicationService orderApplicationService;
    private final StockService stockService;
    private final CouponService couponService;
    private final PaymentGateway paymentGateway;
    private final DistributedLock distributedLock;
    private final TransactionTemplate transactionTemplate;

    @Override
    public RefundResult refund(Long orderId, String reason, String idempotencyKey) {
        validateArgs(orderId, reason);
        String key = ensureKey(idempotencyKey);
        RefundOrder existing = refundOrderMapper.findByIdempotencyKey(key);
        if (existing != null) {
            return toResult(existing);
        }
        return executeLocked(orderId, () -> transactionTemplate.execute(status -> {
            RefundOrder again = refundOrderMapper.findByIdempotencyKey(key);
            if (again != null) {
                return toResult(again);
            }
            Order order = requireOrder(orderId);
            assertRefundable(order, false);
            return doRefund(order, reason, key,
                    OperatorType.USER, order.getUserId(), OrderEvent.REFUND_FULL_SUCCESS);
        }));
    }

    @Override
    public RefundResult merchantReject(Long orderId, Long merchantId, String reason, String idempotencyKey) {
        validateArgs(orderId, reason);
        String key = ensureKey(idempotencyKey);
        RefundOrder existing = refundOrderMapper.findByIdempotencyKey(key);
        if (existing != null) {
            return toResult(existing);
        }
        return executeLocked(orderId, () -> transactionTemplate.execute(status -> {
            RefundOrder again = refundOrderMapper.findByIdempotencyKey(key);
            if (again != null) {
                return toResult(again);
            }
            Order order = requireOrder(orderId);
            assertRefundable(order, true);
            Long operatorId = merchantId != null ? merchantId : order.getMerchantId();
            return doRefund(order, reason, key,
                    OperatorType.MERCHANT, operatorId, OrderEvent.MERCHANT_REJECT);
        }));
    }

    @Override
    public CancelResult cancel(Long orderId, String reason, Integer operatorType, String idempotencyKey) {
        validateArgs(orderId, reason);
        if (operatorType == null || (operatorType != 1 && operatorType != 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "operatorType 只能是 1 用户 或 2 商家");
        }
        String key = ensureKey(idempotencyKey);
        RefundOrder existing = refundOrderMapper.findByIdempotencyKey(key);
        if (existing != null) {
            Order order = orderMapper.selectById(orderId);
            int orderStatus = order == null ? OrderStatus.CANCELLED.getCode() : order.getStatus();
            return new CancelResult(orderId, orderStatus, existing.getRefundNo(), existing.getAmount());
        }

        return executeLocked(orderId, () -> transactionTemplate.execute(status -> {
            RefundOrder again = refundOrderMapper.findByIdempotencyKey(key);
            if (again != null) {
                Order current = orderMapper.selectById(orderId);
                int orderStatus = current == null ? OrderStatus.CANCELLED.getCode() : current.getStatus();
                return new CancelResult(orderId, orderStatus, again.getRefundNo(), again.getAmount());
            }

            Order order = requireOrder(orderId);
            int currentStatus = order.getStatus();
            OperatorType opType = operatorType == 2 ? OperatorType.MERCHANT : OperatorType.USER;
            Long opId = operatorType == 2 ? order.getMerchantId() : order.getUserId();

            if (Objects.equals(currentStatus, OrderStatus.WAIT_PAY.getCode())) {
                releaseReservedAssets(order);
                OrderStatus to = orderApplicationService.transit(orderId, OrderEvent.CANCEL, null,
                        opType, opId, "CANCEL:" + orderId);
                if (orderMapper.markCancelled(orderId, reason) != 1) {
                    throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
                }
                return new CancelResult(orderId, to.getCode(), null, 0L);
            }

            if (Objects.equals(currentStatus, OrderStatus.WAIT_ACCEPT.getCode())
                    || Objects.equals(currentStatus, OrderStatus.PREPARING.getCode())) {
                RefundResult refund = doRefund(order, reason, key, opType, opId,
                        OrderEvent.REFUND_FULL_SUCCESS);
                return new CancelResult(orderId, refund.orderStatus(), refund.refundNo(), refund.amount());
            }

            if (Objects.equals(currentStatus, OrderStatus.CANCELLED.getCode())) {
                RefundOrder latest = refundOrderMapper.findLatestByOrderId(orderId);
                return new CancelResult(
                        orderId,
                        OrderStatus.CANCELLED.getCode(),
                        latest == null ? null : latest.getRefundNo(),
                        latest == null ? 0L : latest.getAmount()
                );
            }

            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "当前订单状态不允许取消");
        }));
    }

    private RefundResult doRefund(Order order, String reason, String idempotencyKey,
                                  OperatorType operatorType, Long operatorId,
                                  OrderEvent transitionEvent) {
        Long orderId = order.getId();
        PaymentInfo payment = paymentGateway.findSuccessfulPayment(orderId);
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND, "订单没有成功支付记录");
        }

        long amount = order.getPayAmount() == null ? 0L : order.getPayAmount();
        if (amount <= 0) {
            throw new BizException(ErrorCode.PAY_AMOUNT_MISMATCH);
        }

        List<OrderItem> items = loadItems(orderId);
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单明细为空");
        }

        RefundOrder refund = new RefundOrder();
        refund.setRefundNo(generateRefundNo());
        refund.setOrderId(orderId);
        refund.setRefundType(1);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.REFUNDING.getCode());
        refund.setIdempotencyKey(idempotencyKey);
        refundOrderMapper.insert(refund);

        for (OrderItem item : items) {
            RefundItem refundItem = new RefundItem();
            refundItem.setRefundId(refund.getId());
            refundItem.setOrderItemId(item.getId());
            refundItem.setSkuId(item.getSkuId());
            refundItem.setQty(item.getQuantity());
            refundItem.setRefundAmount(item.getPayableAmount());
            refundItem.setRestockFlag(1);
            refundItemMapper.insert(refundItem);
        }

        RefundGatewayResult gateway = paymentGateway.refund(orderId, refund.getRefundNo(), amount);
        if (!gateway.success()) {
            throw new BizException(ErrorCode.REFUND_GATEWAY_FAILED, gateway.message());
        }
        if (refundOrderMapper.markSuccess(refund.getId(), gateway.channelRefundNo()) != 1) {
            throw new BizException(ErrorCode.REFUND_STATE_INVALID);
        }

        for (OrderItem item : items) {
            stockService.refund(orderId, order.getShopId(), item.getSkuId(), item.getQuantity());
        }
        OrderDiscount discount = loadCouponDiscount(orderId);
        if (discount != null) {
            couponService.rollback(discount.getSourceId(), orderId);
        }

        OrderStatus to = orderApplicationService.transit(orderId, transitionEvent, null,
                operatorType, operatorId, transitionEvent.name() + ":" + orderId);
        orderItemMapper.markAllRefunded(orderId);
        if (orderMapper.markRefunded(orderId, amount) != 1) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }

        refund.setStatus(RefundStatus.SUCCESS.getCode());
        refund.setChannelRefundNo(gateway.channelRefundNo());
        return new RefundResult(
                refund.getId(),
                refund.getRefundNo(),
                orderId,
                amount,
                RefundStatus.SUCCESS.getCode(),
                to.getCode(),
                gateway.channelRefundNo()
        );
    }

    private void releaseReservedAssets(Order order) {
        for (OrderItem item : loadItems(order.getId())) {
            stockService.release(order.getId(), order.getShopId(), item.getSkuId(), item.getQuantity());
        }
        OrderDiscount discount = loadCouponDiscount(order.getId());
        if (discount != null) {
            couponService.release(discount.getSourceId(), order.getId());
        }
    }

    private void assertRefundable(Order order, boolean rejectOnly) {
        int status = order.getStatus();
        boolean allowed = Objects.equals(status, OrderStatus.WAIT_ACCEPT.getCode())
                || (!rejectOnly && Objects.equals(status, OrderStatus.PREPARING.getCode()));
        if (!allowed) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "当前订单状态不允许退款");
        }
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private List<OrderItem> loadItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
    }

    private OrderDiscount loadCouponDiscount(Long orderId) {
        return orderDiscountMapper.selectOne(new LambdaQueryWrapper<OrderDiscount>()
                .eq(OrderDiscount::getOrderId, orderId)
                .eq(OrderDiscount::getDiscountType, 1)
                .last("LIMIT 1"));
    }

    private RefundResult toResult(RefundOrder refund) {
        Order order = orderMapper.selectById(refund.getOrderId());
        int orderStatus = order == null ? 0 : order.getStatus();
        return new RefundResult(
                refund.getId(),
                refund.getRefundNo(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getStatus(),
                orderStatus,
                refund.getChannelRefundNo()
        );
    }

    private void validateArgs(Long orderId, String reason) {
        if (orderId == null || !StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 和 reason 不能为空");
        }
    }

    private String ensureKey(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString();
    }

    private <T> T executeLocked(Long orderId, Supplier<T> action) {
        return distributedLock.executeWithLock("lock:refund:order:" + orderId, 3, 30, action);
    }

    private String generateRefundNo() {
        String time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RF" + time + random;
    }
}
