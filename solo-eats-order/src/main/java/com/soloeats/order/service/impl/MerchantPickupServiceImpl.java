package com.soloeats.order.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.service.MerchantPickupService;
import com.soloeats.order.service.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MerchantPickupServiceImpl implements MerchantPickupService {

    private final OrderMapper orderMapper;
    private final OrderApplicationService orderApplicationService;

    /** 售后期天数：完成后冻结结算，与配送链路共用同一配置。 */
    @Value("${app.order.after-sales-days:7}")
    private long afterSalesDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderStatus verifyPickup(Long orderId, Long merchantId, String pickupCode) {
        if (orderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 不能为空");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getMerchantId(), merchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        if (!Objects.equals(order.getFulfillmentType(), FulfillmentType.PICKUP.getCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单不是自取订单");
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.WAIT_USER_PICKUP.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单当前状态不可核销");
        }

        String normalizedCode = pickupCode == null ? null : pickupCode.trim();
        if (!StringUtils.hasText(normalizedCode)
                || !Objects.equals(normalizedCode, order.getPickupCode())) {
            // 错误提示不得回显订单上的正确取餐码
            throw new BizException(ErrorCode.PARAM_INVALID, "取餐码不正确");
        }

        orderApplicationService.transit(
                orderId,
                OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP,
                OperatorType.MERCHANT,
                merchantId,
                "PICKUP_VERIFY:" + orderId
        );

        // 自取订单没有配送/送达等后续履约环节，核销即视为履约完成。
        // 此前只推到 PICKED_UP(51) 就结束了，全代码库无人调用 PICKUP_COMPLETE，
        // 导致订单永久停在「已取餐」、settle_eligible_at 始终为空、永远进不了结算。
        orderApplicationService.transit(
                orderId,
                OrderEvent.PICKUP_COMPLETE,
                FulfillmentType.PICKUP,
                OperatorType.SYSTEM,
                null,
                "PICKUP_COMPLETE:" + orderId
        );
        orderMapper.markCompleted(orderId, LocalDateTime.now().plusDays(afterSalesDays));

        // 返回终态：核销后订单已到 COMPLETED，若返回 PICKED_UP 会让前端显示过时的中间状态
        return OrderStatus.COMPLETED;
    }
}
