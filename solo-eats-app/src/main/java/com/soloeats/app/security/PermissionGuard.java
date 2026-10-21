package com.soloeats.app.security;

import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 角色注解之外的数据级权限校验：用户只能操作自己的订单，商家只能操作本商家订单。
 */
@Component
@RequiredArgsConstructor
public class PermissionGuard {

    private final OrderMapper orderMapper;

    public UserPrincipal principal() {
        return AuthContext.require();
    }

    public void assertUser(Long userId) {
        if (!Objects.equals(principal().userId(), userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public void assertMerchant(Long merchantId) {
        if (!Objects.equals(principal().merchantId(), merchantId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public void assertRider(Long riderId) {
        if (!Objects.equals(principal().riderId(), riderId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public Order assertUserOrder(Long orderId) {
        Order order = requireOrder(orderId);
        if (!Objects.equals(order.getUserId(), principal().userId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        return order;
    }

    public Order assertMerchantOrder(Long orderId) {
        Order order = requireOrder(orderId);
        if (!Objects.equals(order.getMerchantId(), principal().merchantId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN);
        }
        return order;
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }
}
