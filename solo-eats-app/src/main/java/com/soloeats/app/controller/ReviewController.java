package com.soloeats.app.controller;

import com.soloeats.app.dto.AppendReviewRequest;
import com.soloeats.app.dto.CreateReviewRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.product.service.ProductReviewService;
import com.soloeats.product.service.command.AppendReviewCommand;
import com.soloeats.product.service.command.CreateReviewCommand;
import com.soloeats.product.service.view.ReviewTagView;
import com.soloeats.product.service.view.ReviewView;
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
 * 用户端评价：提交、门店评价列表（白名单）、我的评价、追评。
 * 提交接口由 @Idempotent + uk_order_item 双重兜底。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class ReviewController {

    private final ProductReviewService productReviewService;
    private final PermissionGuard permissionGuard;

    @RequireRole(Roles.C_USER)
    @Idempotent(ttlSeconds = 60)
    @PostMapping("/reviews")
    public ApiResponse<ReviewView> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(productReviewService.create(
                permissionGuard.principal().userId(),
                new CreateReviewCommand(
                        request.orderId(),
                        request.orderItemId(),
                        request.overallScore(),
                        request.tasteScore(),
                        request.packageScore(),
                        request.deliveryScore(),
                        request.content(),
                        request.images(),
                        request.tags(),
                        request.anonymous()
                )
        ));
    }

    @GetMapping("/shops/{shopId}/reviews")
    public ApiResponse<PageResult<ReviewView>> shopReviews(
            @PathVariable Long shopId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(productReviewService.shopReviews(
                shopId, tag, hasImage, pageNum, pageSize));
    }

    @RequireRole(Roles.C_USER)
    @GetMapping("/reviews/mine")
    public ApiResponse<PageResult<ReviewView>> mine(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(productReviewService.mine(
                permissionGuard.principal().userId(), pageNum, pageSize));
    }

    @RequireRole(Roles.C_USER)
    @Idempotent(ttlSeconds = 60)
    @PostMapping("/reviews/{id}/append")
    public ApiResponse<ReviewView> append(@PathVariable Long id,
                                          @Valid @RequestBody AppendReviewRequest request) {
        return ApiResponse.ok(productReviewService.append(
                permissionGuard.principal().userId(),
                id,
                new AppendReviewCommand(request.content(), request.images())
        ));
    }

    @GetMapping("/review-tags")
    public ApiResponse<List<ReviewTagView>> tags() {
        return ApiResponse.ok(productReviewService.tags());
    }
}
