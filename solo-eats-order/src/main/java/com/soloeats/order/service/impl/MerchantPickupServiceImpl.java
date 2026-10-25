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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MerchantPickupServiceImpl implements MerchantPickupService {

    private final OrderMapper orderMapper;
    private final OrderApplicationService orderApplicationService;

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

        return orderApplicationService.transit(
                orderId,
                OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP,
                OperatorType.MERCHANT,
                merchantId,
                "PICKUP_VERIFY:" + orderId
        );
    }
}
