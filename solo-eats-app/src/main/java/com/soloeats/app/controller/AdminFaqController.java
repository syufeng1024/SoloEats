package com.soloeats.app.controller;

import com.soloeats.app.dto.ReplyFeedbackRequest;
import com.soloeats.app.dto.SaveFaqRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.service.service.FaqService;
import com.soloeats.service.service.FeedbackService;
import com.soloeats.service.service.view.FaqView;
import com.soloeats.service.service.view.FeedbackView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 FAQ 维护与意见反馈处理（接口 18、19）。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;
    private final FeedbackService feedbackService;

    @RequirePermission("service:faq:list")
    @GetMapping("/faqs")
    public ApiResponse<PageResult<FaqView>> faqs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(faqService.pageAdmin(
                category, keyword, status, pageNum, pageSize));
    }

    @RequirePermission("service:faq:list")
    @GetMapping("/faqs/{id}")
    public ApiResponse<FaqView> faqDetail(@PathVariable("id") Long id) {
        return ApiResponse.ok(faqService.detail(id));
    }

    @RequirePermission("service:faq:save")
    @PostMapping("/faqs")
    public ApiResponse<Long> createFaq(@Valid @RequestBody SaveFaqRequest request) {
        return ApiResponse.ok(faqService.create(request.toCommand()));
    }

    @RequirePermission("service:faq:save")
    @PutMapping("/faqs/{id}")
    public ApiResponse<Void> updateFaq(@PathVariable("id") Long id,
                                       @Valid @RequestBody SaveFaqRequest request) {
        faqService.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @RequirePermission("service:faq:save")
    @DeleteMapping("/faqs/{id}")
    public ApiResponse<Void> deleteFaq(@PathVariable("id") Long id) {
        faqService.delete(id);
        return ApiResponse.ok();
    }

    @RequirePermission("service:feedback:list")
    @GetMapping("/feedbacks")
    public ApiResponse<PageResult<FeedbackView>> feedbacks(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(feedbackService.page(status, pageNum, pageSize));
    }

    @RequirePermission("service:feedback:reply")
    @PutMapping("/feedbacks/{id}/reply")
    public ApiResponse<Void> replyFeedback(@PathVariable("id") Long id,
                                           @Valid @RequestBody ReplyFeedbackRequest request) {
        feedbackService.reply(id, request.reply());
        return ApiResponse.ok();
    }
}
