package com.soloeats.app.controller;

import com.soloeats.app.dto.MarketingCalculateRequest;
import com.soloeats.app.dto.MarketingCalculateResponse;
import com.soloeats.app.dto.SeckillReserveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.marketing.service.PromotionService;
import com.soloeats.marketing.service.SeckillReserveResult;
import com.soloeats.marketing.service.SeckillService;
import com.soloeats.marketing.service.view.PromotionAvailableView;
import com.soloeats.marketing.service.view.SeckillSessionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端营销接口。类级前缀是 /api/v1/c 而不是 /api/v1/c/marketing，因为活动列表按约定挂在
 * /api/v1/c/promotions（任务卡接口 3），试算仍保持 /api/v1/c/marketing/calculate。
 *
 * <p>鉴权按端点区分：试算、活动列表、秒杀场次列表属于白名单（未登录可看，userId 为 null）；
 * 秒杀预占/释放通过方法级 {@link RequireRole} 要求 C_USER，userId 只取令牌，不接受请求体传入。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class MarketingClientController {

    private final PromotionService promotionService;
    private final SeckillService seckillService;

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

    /**
     * 秒杀场次列表：未结束的场次 + 各商品余量，按 session_start 升序。白名单，未登录可看。
     */
    @GetMapping("/seckill/sessions")
    public ApiResponse<List<SeckillSessionView>> seckillSessions() {
        return ApiResponse.ok(seckillService.listSessions(null));
    }

    /**
     * 预占秒杀库存。限流 10 次/60 秒（按用户维度），防止刷接口把 Redis 预扣打穿。
     */
    @RequireRole(Roles.C_USER)
    @RateLimit(key = "seckill:reserve", limit = 10, windowSeconds = 60)
    @PostMapping("/seckill/{itemId}/reserve")
    public ApiResponse<SeckillReserveResult> reserve(
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody(required = false) SeckillReserveRequest request) {
        int qty = request == null || request.qty() == null ? 1 : request.qty();
        return ApiResponse.ok(seckillService.reserve(itemId, currentUserId(), qty));
    }

    /**
     * 释放预占。幂等：不存在或已释放时返回成功。
     */
    @RequireRole(Roles.C_USER)
    @PostMapping("/seckill/{itemId}/release")
    public ApiResponse<Void> release(@PathVariable("itemId") Long itemId) {
        seckillService.release(itemId, currentUserId());
        return ApiResponse.ok();
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
