package com.soloeats.app.controller;

import com.soloeats.app.dto.MerchantApplyRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.common.ratelimit.RateLimitDimension;
import com.soloeats.merchant.service.MerchantApplyService;
import com.soloeats.merchant.service.command.AddQualificationCommand;
import com.soloeats.merchant.service.command.ApplyMerchantCommand;
import com.soloeats.merchant.service.command.UpdateMerchantApplyCommand;
import com.soloeats.merchant.service.view.ApplyResult;
import com.soloeats.merchant.service.view.MerchantProgressView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入驻申请（免登录）。除创建外全部要求 applyToken，且 applyId 必须与令牌一致。
 */
@RestController
@RequestMapping("/api/v1/merchant/apply")
@RequiredArgsConstructor
public class MerchantApplyController {

    private static final String APPLY_TOKEN_HEADER = "X-Apply-Token";

    private final MerchantApplyService merchantApplyService;

    @RateLimit(key = "merchant-apply", limit = 5, windowSeconds = 60,
            dimension = RateLimitDimension.IP)
    @Idempotent(ttlSeconds = 60)
    @PostMapping
    public ApiResponse<ApplyResult> apply(@Valid @RequestBody MerchantApplyRequest request) {
        return ApiResponse.ok(merchantApplyService.apply(new ApplyMerchantCommand(
                request.merchantName(),
                request.brandName(),
                request.contactName(),
                request.contactPhone(),
                request.smsCode(),
                request.categoryId())));
    }

    @PutMapping("/{applyId}")
    public ApiResponse<MerchantProgressView> updateApply(
            @PathVariable Long applyId,
            @Valid @RequestBody MerchantApplyRequest.UpdateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(merchantApplyService.updateApply(
                applyId,
                resolveApplyToken(httpRequest),
                new UpdateMerchantApplyCommand(
                        request.businessLicenseNo(),
                        request.legalPerson(),
                        request.legalIdCard(),
                        request.province(),
                        request.city(),
                        request.district(),
                        request.address())));
    }

    @PostMapping("/{applyId}/qualifications")
    public ApiResponse<Void> addQualification(
            @PathVariable Long applyId,
            @Valid @RequestBody MerchantApplyRequest.QualificationRequest request,
            HttpServletRequest httpRequest) {
        merchantApplyService.addQualification(applyId, resolveApplyToken(httpRequest),
                new AddQualificationCommand(request.qualType(), request.fileId()));
        return ApiResponse.ok();
    }

    @PostMapping("/{applyId}/submit")
    public ApiResponse<Void> submit(@PathVariable Long applyId, HttpServletRequest httpRequest) {
        merchantApplyService.submit(applyId, resolveApplyToken(httpRequest));
        return ApiResponse.ok();
    }

    @GetMapping("/{applyId}")
    public ApiResponse<MerchantProgressView> progress(@PathVariable Long applyId,
                                                      HttpServletRequest httpRequest) {
        return ApiResponse.ok(merchantApplyService.progress(applyId, resolveApplyToken(httpRequest)));
    }

    private String resolveApplyToken(HttpServletRequest request) {
        String token = request.getHeader(APPLY_TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            String authorization = request.getHeader("Authorization");
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
        }
        return token;
    }
}
