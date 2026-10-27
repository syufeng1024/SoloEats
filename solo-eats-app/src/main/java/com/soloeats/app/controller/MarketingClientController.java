package com.soloeats.app.controller;

import com.soloeats.app.dto.MarketingCalculateRequest;
import com.soloeats.app.dto.MarketingCalculateResponse;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.marketing.service.PromotionService;
import com.soloeats.marketing.service.view.PromotionAvailableView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端营销接口。两个端点都属于白名单：与 /c/freight/estimate 同级，未登录也返回结果，
 * 此时 userId 为 null；业务计算全部在 PromotionService。
 *
 * <p>类级前缀是 /api/v1/c 而不是 /api/v1/c/marketing，因为活动列表按约定挂在
 * /api/v1/c/promotions（任务卡接口 3），试算仍保持 /api/v1/c/marketing/calculate。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class MarketingClientController {

    private final PromotionService promotionService;

    @PostMapping("/marketing/calculate")
    public ApiResponse<MarketingCalculateResponse> calculate(
            @Valid @RequestBody MarketingCalculateRequest request) {
        UserPrincipal principal = AuthContext.current();
        Long userId = principal == null ? null : principal.userId();
        return ApiResponse.ok(
                MarketingCalculateResponse.from(promotionService.calculate(request.toCommand(userId))));
    }

    /**
     * 用户端可见活动：进行中 + 命中本次门店/商品范围。不要求登录，未登录只按 shopId/spuIds 判定。
     *
     * @param spuIds 支持重复参数（spuIds=1&spuIds=2）或逗号分隔（spuIds=1,2）
     */
    @GetMapping("/promotions")
    public ApiResponse<List<PromotionAvailableView>> promotions(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) List<Long> spuIds) {
        return ApiResponse.ok(promotionService.listAvailable(shopId, spuIds, null));
    }
}
