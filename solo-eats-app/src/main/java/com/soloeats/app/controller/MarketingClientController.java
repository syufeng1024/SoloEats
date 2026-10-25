package com.soloeats.app.controller;

import com.soloeats.app.dto.MarketingCalculateRequest;
import com.soloeats.app.dto.MarketingCalculateResponse;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.marketing.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端营销试算。白名单接口：与 /c/freight/estimate 同级，未登录也返回试算结果，
 * 此时 userId 为 null；业务计算全部在 PromotionService。
 */
@RestController
@RequestMapping("/api/v1/c/marketing")
@RequiredArgsConstructor
public class MarketingClientController {

    private final PromotionService promotionService;

    @PostMapping("/calculate")
    public ApiResponse<MarketingCalculateResponse> calculate(
            @Valid @RequestBody MarketingCalculateRequest request) {
        UserPrincipal principal = AuthContext.current();
        Long userId = principal == null ? null : principal.userId();
        return ApiResponse.ok(
                MarketingCalculateResponse.from(promotionService.calculate(request.toCommand(userId))));
    }
}
