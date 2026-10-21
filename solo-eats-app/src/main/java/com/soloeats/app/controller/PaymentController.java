package com.soloeats.app.controller;

import com.soloeats.app.dto.PayRequest;
import com.soloeats.app.dto.PaymentCallbackRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.app.vo.PaymentVO;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.common.ratelimit.RateLimitDimension;
import com.soloeats.payment.service.PaymentCallbackCommand;
import com.soloeats.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PermissionGuard permissionGuard;

    @RequireRole(Roles.C_USER)
    @RateLimit(key = "payment-pay", limit = 20, windowSeconds = 60, dimension = RateLimitDimension.USER)
    @PostMapping("/pay")
    public ApiResponse<PaymentVO> pay(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        permissionGuard.assertUserOrder(request.orderId());
        return ApiResponse.ok(
                PaymentVO.from(paymentService.pay(request.orderId(), request.channel(), idempotencyKey))
        );
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(@Valid @RequestBody PaymentCallbackRequest request) {
        paymentService.callback(new PaymentCallbackCommand(
                request.paymentNo(),
                request.tradeNo(),
                request.amount(),
                request.sign()
        ));
        return ApiResponse.ok();
    }
}
