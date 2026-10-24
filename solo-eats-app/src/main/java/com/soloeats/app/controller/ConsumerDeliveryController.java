package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.view.DeliveryTrackView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端配送轨迹。只返回坐标与心跳时间，不返回骑手手机号/姓名。
 */
@RequireRole(Roles.C_USER)
@RestController
@RequestMapping("/api/v1/c/deliveries")
@RequiredArgsConstructor
public class ConsumerDeliveryController {

    private final DeliveryTrackService deliveryTrackService;

    @GetMapping("/{deliveryId}/track")
    public ApiResponse<DeliveryTrackView> track(@PathVariable("deliveryId") Long deliveryId) {
        return ApiResponse.ok(deliveryTrackService.trackForConsumer(deliveryId));
    }
}
