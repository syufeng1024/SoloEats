package com.soloeats.app.controller;

import com.soloeats.app.dto.CreateComplaintRequest;
import com.soloeats.app.dto.CreateFeedbackRequest;
import com.soloeats.app.dto.CreateTicketRequest;
import com.soloeats.app.dto.SatisfactionRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.service.service.ComplaintService;
import com.soloeats.service.service.FaqService;
import com.soloeats.service.service.FeedbackService;
import com.soloeats.service.service.TicketService;
import com.soloeats.service.service.command.CreateComplaintCommand;
import com.soloeats.service.service.command.CreateTicketCommand;
import com.soloeats.service.service.view.ComplaintCreatedView;
import com.soloeats.service.service.view.FaqView;
import com.soloeats.service.service.view.TicketCreatedView;
import com.soloeats.service.service.view.TicketDetailView;
import com.soloeats.service.service.view.TicketView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端客服入口：工单、投诉、FAQ、意见反馈。
 *
 * <p>提交者身份一律取 {@link AuthContext#require()} 的 userId（消费者为 t_member_user.id），
 * 请求体不接受 userId。FAQ 为帮助中心白名单接口，不做角色拦截。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class ConsumerTicketController {

    private static final int SUBMITTER_USER = 1;
    private static final int SOURCE_USER = 1;

    private final TicketService ticketService;
    private final ComplaintService complaintService;
    private final FaqService faqService;
    private final FeedbackService feedbackService;

    @RequireRole(Roles.C_USER)
    @Idempotent(ttlSeconds = 10)
    @PostMapping("/tickets")
    public ApiResponse<TicketCreatedView> createTicket(
            @Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.createTicket(new CreateTicketCommand(
                request.ticketType(),
                request.orderId(),
                request.subject(),
                request.content(),
                request.images(),
                request.priority(),
                SOURCE_USER,
                SUBMITTER_USER,
                currentUserId())));
    }

    @RequireRole(Roles.C_USER)
    @GetMapping("/tickets")
    public ApiResponse<PageResult<TicketView>> myTickets(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(ticketService.pageMine(
                currentUserId(), status, pageNum, pageSize));
    }

    @RequireRole(Roles.C_USER)
    @GetMapping("/tickets/{id}")
    public ApiResponse<TicketDetailView> myTicket(@PathVariable("id") Long id) {
        return ApiResponse.ok(ticketService.detailForConsumer(id, currentUserId()));
    }

    @RequireRole(Roles.C_USER)
    @PutMapping("/tickets/{id}/close")
    public ApiResponse<Void> closeTicket(@PathVariable("id") Long id) {
        ticketService.closeByConsumer(id, currentUserId());
        return ApiResponse.ok();
    }

    @RequireRole(Roles.C_USER)
    @PutMapping("/tickets/{id}/satisfaction")
    public ApiResponse<Void> rateTicket(@PathVariable("id") Long id,
                                        @Valid @RequestBody SatisfactionRequest request) {
        ticketService.rateByConsumer(id, currentUserId(),
                request.satisfaction(), request.satisfactionRemark());
        return ApiResponse.ok();
    }

    @RequireRole(Roles.C_USER)
    @PostMapping("/complaints")
    public ApiResponse<ComplaintCreatedView> createComplaint(
            @Valid @RequestBody CreateComplaintRequest request) {
        return ApiResponse.ok(complaintService.create(new CreateComplaintCommand(
                request.orderId(),
                request.targetType(),
                request.targetId(),
                request.reason(),
                request.images(),
                currentUserId())));
    }

    /** 帮助中心白名单：不要求登录，只返回启用状态的 FAQ。 */
    @GetMapping("/faqs")
    public ApiResponse<List<FaqView>> faqs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(faqService.listPublic(category, keyword));
    }

    @RequireRole(Roles.C_USER)
    @PostMapping("/feedbacks")
    public ApiResponse<Long> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        return ApiResponse.ok(feedbackService.create(
                currentUserId(), request.content(), request.images(), request.contact()));
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
