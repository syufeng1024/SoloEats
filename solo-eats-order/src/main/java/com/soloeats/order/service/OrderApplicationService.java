package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderDiscount;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.entity.OrderStatusLog;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.domain.statemachine.OrderStateMachine;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.order.service.event.OrderCreatedEvent;
import com.soloeats.order.service.result.CreateOrderResult;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.service.SkuService;
import com.soloeats.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单应用服务。
 * 下单链路的本地事务边界：订单、明细、优惠快照、库存锁定、库存流水、状态流水。
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDiscountMapper orderDiscountMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final OrderStateMachine orderStateMachine;
    private final SkuService skuService;
    private final StockService stockService;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    @Idempotent(ttlSeconds = 86400)
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResult createOrder(CreateOrderCommand command, String idempotencyKey) {
        validate(command);

        long packagingFee = Math.max(0L, Objects.requireNonNullElse(command.packagingFee(), 0L));
        long deliveryFee = command.fulfillmentType() == 2
                ? 0L
                : Math.max(0L, Objects.requireNonNullElse(command.deliveryFee(), 0L));

        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (CreateOrderCommand.ItemCommand item : command.items()) {
            qtyBySku.merge(item.skuId(), item.quantity(), Integer::sum);
        }

        List<ItemDraft> drafts = new ArrayList<>();
        long goodsAmount = 0L;
        for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
            Sku sku = skuService.getRequired(entry.getKey(), command.shopId());
            try {
                long originalAmount = Math.multiplyExact(sku.getPrice(), entry.getValue().longValue());
                goodsAmount = Math.addExact(goodsAmount, originalAmount);
                drafts.add(new ItemDraft(sku, entry.getValue(), originalAmount));
            } catch (ArithmeticException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "订单金额溢出");
            }
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(command.userId());
        order.setMerchantId(command.merchantId());
        order.setShopId(command.shopId());
        order.setFulfillmentType(command.fulfillmentType());
        order.setStatus(OrderStatus.WAIT_PAY.getCode());
        order.setPaymentStatus(0);
        order.setRefundStatus(0);
        order.setExceptionType(0);
        order.setSettlementStatus(0);
        order.setGoodsAmount(goodsAmount);
        order.setPackagingFee(packagingFee);
        order.setDeliveryFee(deliveryFee);
        order.setDiscountAmount(0L);
        order.setPlatformDiscount(0L);
        order.setMerchantDiscount(0L);
        order.setPayAmount(goodsAmount + packagingFee + deliveryFee);
        order.setRefundedAmount(0L);
        order.setReceiverName(command.receiverName());
        order.setReceiverPhone(command.receiverPhone());
        order.setReceiverAddress(command.receiverAddress());
        order.setPickupPhone(command.pickupPhone());
        order.setRemark(command.remark());
        order.setVersion(0);
        orderMapper.insert(order);

        long discountAmount = 0L;
        long platformDiscount = 0L;
        long merchantDiscount = 0L;
        CouponLockResult coupon = null;
        if (command.couponId() != null) {
            coupon = couponService.lock(command.userId(), command.couponId(), order.getId(), goodsAmount);
            discountAmount = coupon.discountAmount();
            if (Objects.equals(coupon.bearer(), 2)) {
                merchantDiscount = discountAmount;
            } else {
                platformDiscount = discountAmount;
            }
            long payAmount = goodsAmount + packagingFee + deliveryFee - discountAmount;
            if (payAmount < 1L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "实付金额不能低于 0.01 元");
            }
            order.setDiscountAmount(discountAmount);
            order.setPlatformDiscount(platformDiscount);
            order.setMerchantDiscount(merchantDiscount);
            order.setPayAmount(payAmount);
            if (orderMapper.updateById(order) != 1) {
                throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
            }
        }

        Map<Long, Long> apportion = apportionDiscount(discountAmount, drafts, goodsAmount);
        List<CreateOrderResult.ItemResult> itemResults = new ArrayList<>();
        for (ItemDraft draft : drafts) {
            long itemDiscount = apportion.getOrDefault(draft.sku().getId(), 0L);
            long itemPayable = draft.originalAmount() - itemDiscount;

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setShopId(command.shopId());
            item.setSpuId(draft.sku().getSpuId());
            item.setSkuId(draft.sku().getId());
            item.setSkuName(draft.sku().getSkuName());
            item.setSpecJson(draft.sku().getSpecJson());
            item.setImageUrl(draft.sku().getImageUrl());
            item.setOriginalUnitPrice(draft.sku().getPrice());
            item.setQuantity(draft.quantity());
            item.setOriginalAmount(draft.originalAmount());
            item.setDiscountAmount(itemDiscount);
            item.setPlatformDiscount(Objects.equals(coupon == null ? null : coupon.bearer(), 1) ? itemDiscount : 0L);
            item.setMerchantDiscount(Objects.equals(coupon == null ? null : coupon.bearer(), 2) ? itemDiscount : 0L);
            item.setPayableAmount(itemPayable);
            item.setRefundedQty(0);
            item.setRefundedAmount(0L);
            item.setRestockFlag(0);
            orderItemMapper.insert(item);

            stockService.lock(order.getId(), command.shopId(), draft.sku().getId(), draft.quantity());
            itemResults.add(new CreateOrderResult.ItemResult(
                    draft.sku().getId(), draft.sku().getSkuName(), draft.quantity(), draft.sku().getPrice(),
                    draft.originalAmount(), itemDiscount, itemPayable));
        }

        if (coupon != null) {
            OrderDiscount orderDiscount = new OrderDiscount();
            orderDiscount.setOrderId(order.getId());
            orderDiscount.setDiscountType(1);
            orderDiscount.setSourceId(coupon.userCouponId());
            orderDiscount.setBearer(coupon.bearer());
            orderDiscount.setAmount(discountAmount);
            orderDiscount.setApportionJson(toJson(apportion));
            orderDiscountMapper.insert(orderDiscount);
        }

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setFromStatus(-1);
        statusLog.setToStatus(OrderStatus.WAIT_PAY.getCode());
        statusLog.setEvent("CREATE");
        statusLog.setOperatorType(OperatorType.USER.getCode());
        statusLog.setOperatorId(command.userId());
        statusLog.setRequestId(StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString());
        orderStatusLogMapper.insert(statusLog);

        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));

        return new CreateOrderResult(
                order.getId(), order.getOrderNo(), order.getStatus(), order.getFulfillmentType(),
                order.getGoodsAmount(), order.getPackagingFee(), order.getDeliveryFee(),
                order.getDiscountAmount(), order.getPlatformDiscount(), order.getMerchantDiscount(),
                order.getPayAmount(), order.getPickupCode(), itemResults);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderStatus transit(Long orderId,
                               OrderEvent event,
                               FulfillmentType fulfillmentType,
                               OperatorType operatorType,
                               Long operatorId,
                               String requestId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        OrderStatus from = OrderStatus.fromCode(order.getStatus());
        FulfillmentType type = fulfillmentType != null
                ? fulfillmentType
                : FulfillmentType.fromCode(order.getFulfillmentType());
        OrderStatus to = orderStateMachine.next(from, event, type);

        int version = order.getVersion() == null ? 0 : order.getVersion();
        int rows = orderMapper.updateStatus(orderId, from.getCode(), to.getCode(), version);
        if (rows != 1) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(from.getCode());
        log.setToStatus(to.getCode());
        log.setEvent(event.name());
        log.setOperatorType((operatorType == null ? OperatorType.SYSTEM : operatorType).getCode());
        log.setOperatorId(operatorId);
        log.setRequestId(StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString());
        orderStatusLogMapper.insert(log);
        return to;
    }

    private void validate(CreateOrderCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        if (command.userId() == null || command.merchantId() == null || command.shopId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId、merchantId、shopId 不能为空");
        }
        if (command.fulfillmentType() == null
                || (command.fulfillmentType() != 1 && command.fulfillmentType() != 2)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "履约方式只能是 1配送 或 2自取");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单明细不能为空");
        }
        for (CreateOrderCommand.ItemCommand item : command.items()) {
            if (item == null || item.skuId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "skuId 不能为空，quantity 必须大于 0");
            }
        }
        if (command.packagingFee() != null && command.packagingFee() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "packagingFee 不能为负数");
        }
        if (command.deliveryFee() != null && command.deliveryFee() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "deliveryFee 不能为负数");
        }
        if (command.fulfillmentType() == 1) {
            if (!StringUtils.hasText(command.receiverName())
                    || !StringUtils.hasText(command.receiverPhone())
                    || !StringUtils.hasText(command.receiverAddress())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "配送单必须填写收货人、手机号和地址");
            }
        } else if (!StringUtils.hasText(command.pickupPhone())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "自取单必须填写自取人手机号");
        }
    }

    private Map<Long, Long> apportionDiscount(long discountAmount, List<ItemDraft> drafts, long goodsAmount) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (discountAmount <= 0L) {
            for (ItemDraft draft : drafts) {
                result.put(draft.sku().getId(), 0L);
            }
            return result;
        }

        long allocated = 0L;
        for (ItemDraft draft : drafts) {
            long share = BigDecimal.valueOf(discountAmount)
                    .multiply(BigDecimal.valueOf(draft.originalAmount()))
                    .divide(BigDecimal.valueOf(goodsAmount), 0, RoundingMode.HALF_UP)
                    .longValue();
            share = Math.min(share, draft.originalAmount());
            result.put(draft.sku().getId(), share);
            allocated += share;
        }

        long remainder = discountAmount - allocated;
        if (remainder > 0L) {
            ItemDraft largest = drafts.stream()
                    .max(Comparator.comparingLong(ItemDraft::originalAmount))
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单明细为空"));
            long current = result.getOrDefault(largest.sku().getId(), 0L);
            long capacity = largest.originalAmount() - current;
            if (capacity < remainder) {
                throw new BizException(ErrorCode.PARAM_INVALID, "优惠分摊失败");
            }
            result.put(largest.sku().getId(), current + remainder);
        }
        return result;
    }

    private String toJson(Map<Long, Long> apportion) {
        return apportion.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String generateOrderNo() {
        String time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "SO" + time + random;
    }

    private record ItemDraft(Sku sku, int quantity, long originalAmount) {
    }
}
