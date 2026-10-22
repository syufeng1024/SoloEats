package com.soloeats.app.controller;

import com.soloeats.app.dto.ReplyReviewRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.ProductReviewService;
import com.soloeats.product.service.command.ReplyReviewCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/reviews")
@RequiredArgsConstructor
public class MerchantReviewController {

    private final ProductReviewService productReviewService;
    private final PermissionGuard permissionGuard;

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
