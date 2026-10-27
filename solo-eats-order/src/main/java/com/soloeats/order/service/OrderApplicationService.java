package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.marketing.service.DiscountApportioner;
import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.member.service.view.OrderAddressView;
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
import com.soloeats.order.service.event.OrderStatusChangedEvent;
import com.soloeats.order.service.result.CreateOrderResult;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.service.SkuService;
import com.soloeats.product.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    /** 规格书 9.8 保底：实付金额不得低于 0.01 元（= 1 分）。 */
    private static final long MIN_PAY_AMOUNT = 1L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDiscountMapper orderDiscountMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final OrderStateMachine orderStateMachine;
    private final SkuService skuService;
    private final StockService stockService;
    private final CouponService couponService;
    private final MemberAddressService memberAddressService;
    private final ApplicationEventPublisher eventPublisher;

    @Idempotent(ttlSeconds = 86400)
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResult createOrder(CreateOrderCommand command, String idempotencyKey) {
        validate(command);
        ReceiverInfo receiver = resolveReceiver(command);
        validateReceiver(command, receiver);

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
        long payableBase = 0L;
        for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
            Sku sku = skuService.getRequired(entry.getKey(), command.shopId());
            try {
                long originalAmount = Math.multiplyExact(sku.getPrice(), entry.getValue().longValue());
                goodsAmount = Math.addExact(goodsAmount, originalAmount);
                // 规格书 9.7 要求优惠按「商品折后金额」比例分摊。当前下单链路的行级优惠只有 SKU 售价
                // （活动价 / 会员价尚未接入下单落库），因此行折后应付 = 行原价；
                // 后续接入行级优惠时只需在此处扣减，分摊逻辑无需改动。
                long linePayableAmount = originalAmount;
                payableBase = Math.addExact(payableBase, linePayableAmount);
                drafts.add(new ItemDraft(sku, entry.getValue(), originalAmount, linePayableAmount));
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
        long baseAmount = sumAmount(goodsAmount, packagingFee, deliveryFee);
        // 规格书 9.8 保底的第一道拦截：无任何优惠的基数已不足 0.01 元时，
        // 后续折扣只会更低，直接拒绝，且不落库，避免留下永远无法支付的僵尸订单。
        assertPayAmount(baseAmount);
        order.setPayAmount(baseAmount);
        order.setRefundedAmount(0L);
        order.setAddressId(receiver.addressId());
        order.setReceiverName(receiver.receiverName());
        order.setReceiverPhone(receiver.receiverPhone());
        order.setReceiverAddress(receiver.receiverAddress());
        order.setReceiverLng(receiver.lng());
        order.setReceiverLat(receiver.lat());
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
        }

        // 规格书 9.7「只允许服务端最终计算一次」+ 9.8「实付 ≥ 0.01 元」：
        // 这是有折扣（券 / 满减 / 会员价 / 活动价）汇合后的唯一出口，
        // 保底校验放在分支外，任何折扣路径都被同一次校验兜住。
        long finalPayAmount = baseAmount - discountAmount;
        assertPayAmount(finalPayAmount);
        if (coupon != null) {
            order.setDiscountAmount(discountAmount);
            order.setPlatformDiscount(platformDiscount);
            order.setMerchantDiscount(merchantDiscount);
            order.setPayAmount(finalPayAmount);
            if (orderMapper.updateById(order) != 1) {
                throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
            }
        }

        Map<Long, Long> apportion = apportionDiscount(discountAmount, drafts, payableBase);
        List<CreateOrderResult.ItemResult> itemResults = new ArrayList<>();
        for (ItemDraft draft : drafts) {
            long itemDiscount = apportion.getOrDefault(draft.sku().getId(), 0L);
            long itemPayable = draft.payableAmount() - itemDiscount;

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
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, order.getUserId(), order.getOrderNo(), from.getCode(), to.getCode()));
        return to;
    }

    /**
     * 金额求和（分）。溢出时按参数非法拒绝，避免 {@code long} 回绕出负数或异常小的金额。
     */
    private long sumAmount(long... parts) {
        try {
            long total = 0L;
            for (long part : parts) {
                total = Math.addExact(total, part);
            }
            return total;
        } catch (ArithmeticException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单金额溢出");
        }
    }

    /**
     * 实付保底校验（规格书 9.8：不允许 0 元或负数成交）。
     *
     * @param payAmount 服务端最终计算的实付金额（分）
     */
    private void assertPayAmount(long payAmount) {
        if (payAmount < MIN_PAY_AMOUNT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实付金额不能低于 0.01 元");
        }
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
    }

    /**
     * 收货信息解析：addressId 优先（归属校验与解密都在 member 模块内，越权抛 10004），
     * 未传地址簿时使用请求里的文本与坐标。坐标必须成对，只有一边按缺失处理并告警。
     */
    private ReceiverInfo resolveReceiver(CreateOrderCommand command) {
        Long addressId = command.addressId();
        String receiverName = command.receiverName();
        String receiverPhone = command.receiverPhone();
        String receiverAddress = command.receiverAddress();
        BigDecimal lng = command.receiverLng();
        BigDecimal lat = command.receiverLat();

        if (addressId != null) {
            OrderAddressView address = memberAddressService.resolveForOrder(
                    command.userId(), addressId);
            receiverName = address.receiverName();
            receiverPhone = address.receiverPhone();
            receiverAddress = address.address();
            lng = address.lng() != null ? address.lng() : lng;
            lat = address.lat() != null ? address.lat() : lat;
        }

        if (lng == null || lat == null) {
            lng = null;
            lat = null;
            if (Objects.equals(command.fulfillmentType(), FulfillmentType.DELIVERY.getCode())) {
                log.warn("订单无收货坐标，配送将无法按距离调度 userId={} shopId={} addressId={}",
                        command.userId(), command.shopId(), addressId);
            }
        }
        return new ReceiverInfo(addressId, receiverName, receiverPhone, receiverAddress, lng, lat);
    }

    private void validateReceiver(CreateOrderCommand command, ReceiverInfo receiver) {
        if (Objects.equals(command.fulfillmentType(), FulfillmentType.DELIVERY.getCode())) {
            if (!StringUtils.hasText(receiver.receiverName())
                    || !StringUtils.hasText(receiver.receiverPhone())
                    || !StringUtils.hasText(receiver.receiverAddress())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "配送单必须填写收货人、手机号和地址");
            }
        } else if (!StringUtils.hasText(command.pickupPhone())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "自取单必须填写自取人手机号");
        }
    }

    /**
     * 优惠分摊：把一笔整单优惠按各项「折后应付金额」占比拆到明细上（规格书 9.7）。
     *
     * <p>不变量（由 {@link DiscountApportioner} 保证）：
     * <ul>
     *   <li>Σ分摊 == min(优惠总额, Σ折后应付)；</li>
     *   <li>单项分摊 ≤ 该项折后应付，且不为负数；</li>
     *   <li>余数逐分归入仍有余量的最大项，不再出现「优惠分摊失败」。</li>
     * </ul>
     *
     * @param discountAmount 整单优惠总额（分），非正数表示无优惠
     * @param drafts         订单明细草稿，{@code payableAmount()} 为行折后应付（分摊基数）
     * @param payableBase    Σ折后应付（分），用于校验基数自洽
     * @return skuId → 分摊优惠（分），顺序与 drafts 一致
     */
    private Map<Long, Long> apportionDiscount(long discountAmount, List<ItemDraft> drafts, long payableBase) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (drafts.isEmpty()) {
            // 明细为空说明上游校验失效，此处不静默吞掉优惠
            if (discountAmount > 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "订单明细为空，无法分摊优惠");
            }
            return result;
        }

        List<DiscountApportionItem> items = new ArrayList<>(drafts.size());
        long sumPayable = 0L;
        for (ItemDraft draft : drafts) {
            items.add(new DiscountApportionItem(draft.sku().getId(), draft.payableAmount(), 0L));
            sumPayable = Math.addExact(sumPayable, draft.payableAmount());
        }
        if (sumPayable != payableBase) {
            // 基数不自洽时继续分摊会算出错误明细金额，直接拒绝而不是取近似值
            throw new BizException(ErrorCode.PARAM_INVALID, "优惠分摊基数不一致");
        }
        if (discountAmount <= 0L) {
            for (ItemDraft draft : drafts) {
                result.put(draft.sku().getId(), 0L);
            }
            return result;
        }
        if (sumPayable <= 0L) {
            // 没有可分摊的应付金额却存在优惠，属于数据异常
            throw new BizException(ErrorCode.PARAM_INVALID, "没有可分摊的优惠金额");
        }

        for (DiscountApportionItem item : DiscountApportioner.apportion(items, discountAmount)) {
            result.put(item.skuId(), item.discountAmount());
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

    /**
     * 下单明细草稿。
     *
     * @param sku             SKU 快照
     * @param quantity        购买数量
     * @param originalAmount  行原价小计（分）
     * @param payableAmount   行折后应付（分），优惠分摊基数
     */
    private record ItemDraft(Sku sku, int quantity, long originalAmount, long payableAmount) {
    }

    private record ReceiverInfo(
            Long addressId,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            BigDecimal lng,
            BigDecimal lat
    ) {
    }
}
