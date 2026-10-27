package com.soloeats.app.controller;

import com.soloeats.app.dto.ReplyReviewRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.ProductReviewService;
import com.soloeats.product.service.command.ReplyReviewCommand;
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

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/reviews")
@RequiredArgsConstructor
public class MerchantReviewController {

    private final ProductReviewService productReviewService;
    private final PermissionGuard permissionGuard;

    /**
     * 商家评价列表。merchantId 取登录令牌，与 reply 一致；
     * 服务层按该商家全部门店过滤，只返回已审核通过的评价。
     */
    @GetMapping
    public ApiResponse<PageResult<ReviewView>> list(
            @RequestParam(required = false) Integer scoreLevel,
            @RequestParam(required = false) Integer replyStatus,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(productReviewService.merchantPage(
                permissionGuard.requireMerchantId(), scoreLevel, replyStatus, pageNum, pageSize));
    }

    @OperationLog(module = "评价管理", action = "商家回复评价")
    @PostMapping("/{id}/reply")
    public ApiResponse<Void> reply(@PathVariable Long id,
                                   @Valid @RequestBody ReplyReviewRequest request) {
        productReviewService.reply(
                permissionGuard.requireMerchantId(),
                id,
                new ReplyReviewCommand(request.reply()));
        return ApiResponse.ok();
    }
}
