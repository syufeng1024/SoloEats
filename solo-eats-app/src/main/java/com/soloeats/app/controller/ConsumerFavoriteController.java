package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveFavoriteRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.member.service.FavoriteService;
import com.soloeats.member.service.view.FavoriteView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端收藏。userId 只取令牌；重复收藏/重复取消都返回成功。
 */
@RestController
@RequestMapping("/api/v1/c/favorites")
@RequireRole(Roles.C_USER)
@RequiredArgsConstructor
public class ConsumerFavoriteController {

    private final FavoriteService favoriteService;

    @Idempotent(ttlSeconds = 5)
    @PostMapping
    public ApiResponse<FavoriteView> add(@Valid @RequestBody SaveFavoriteRequest request) {
        return ApiResponse.ok(favoriteService.add(
                currentUserId(), request.targetType(), request.targetId()));
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public ApiResponse<Void> remove(@PathVariable("targetType") Integer targetType,
                                    @PathVariable("targetId") Long targetId) {
        favoriteService.remove(currentUserId(), targetType, targetId);
        return ApiResponse.ok();
    }

    /** targetType 为空返回全部；targetId 用于店铺页判断单个对象是否已收藏。 */
    @GetMapping
    public ApiResponse<PageResult<FavoriteView>> page(
            @RequestParam(required = false) Integer targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(favoriteService.page(
                currentUserId(), targetType, targetId, pageNum, pageSize));
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
