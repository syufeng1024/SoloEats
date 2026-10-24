package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.view.DeliveryTrackView;
import com.soloeats.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端配送轨迹。只返回坐标与心跳时间，不返回骑手手机号/姓名。
 * 越权校验在 app 层完成：delivery 不依赖 order，订单归属由 OrderQueryService 判定。
 */
@RequireRole(Roles.C_USER)
@RestController
@RequestMapping("/api/v1/c/deliveries")
@RequiredArgsConstructor
public class ConsumerDeliveryController {

    private final DeliveryTrackService deliveryTrackService;
    private final DeliveryService deliveryService;
    private final OrderQueryService orderQueryService;

    @GetMapping("/{deliveryId}/track")
    public ApiResponse<DeliveryTrackView> track(@PathVariable("deliveryId") Long deliveryId) {
        DeliveryOrder delivery = deliveryService.getById(deliveryId);
        if (delivery == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        Long userId = AuthContext.require().userId();
        if (!orderQueryService.belongsToUser(delivery.getOrderId(), userId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "该配送单不属于当前用户");
        }
        return ApiResponse.ok(deliveryTrackService.trackForConsumer(deliveryId));
    }
}
