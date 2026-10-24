package com.soloeats.app.controller;

import com.soloeats.app.dto.FreightEstimateRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.delivery.service.FreightService;
import com.soloeats.delivery.service.view.FreightEstimateView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端运费试算。白名单接口：下单前未登录也要能看到配送费，故不加 @RequireRole。
 */
@RestController
@RequestMapping("/api/v1/c/freight")
@RequiredArgsConstructor
public class ConsumerFreightController {

    private final FreightService freightService;

    @PostMapping("/estimate")
    public ApiResponse<FreightEstimateView> estimate(
            @Valid @RequestBody FreightEstimateRequest request) {
        return ApiResponse.ok(freightService.estimate(request.toCommand()));
    }
}
