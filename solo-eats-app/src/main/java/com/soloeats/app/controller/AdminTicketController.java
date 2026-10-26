package com.soloeats.app.controller;

import com.soloeats.app.dto.ComplaintJudgeRequest;
import com.soloeats.app.dto.TicketAssignRequest;
import com.soloeats.app.dto.TicketCallbackRequest;
import com.soloeats.app.dto.TicketHandleRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.service.service.ComplaintService;
import com.soloeats.service.service.TicketService;
import com.soloeats.service.service.query.TicketQuery;
import com.soloeats.service.service.view.TicketDetailView;
import com.soloeats.service.service.view.TicketStatsView;
import com.soloeats.service.service.view.TicketView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台工单处理与投诉责任判定。权限码由 V25 种入 t_sys_menu（id 120~130）。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketService ticketService;
    private final ComplaintService complaintService;

    @RequirePermission("service:ticket:list")
    @GetMapping("/tickets")
    public ApiResponse<PageResult<TicketView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer ticketType,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(ticketService.pageAdmin(
                new TicketQuery(status, ticketType, assigneeId, priority, dateFrom, dateTo),
                pageNum, pageSize));
    }

    /** 接待量 / 办结率 / 平均响应时长 / 满意度。会话接待量待阶段B。 */
    @RequirePermission("service:ticket:list")
    @GetMapping("/tickets/stats")
    public ApiResponse<TicketStatsView> stats(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(ticketService.stats(dateFrom, dateTo));
    }

    @RequirePermission("service:ticket:detail")
    @GetMapping("/tickets/{id}")
    public ApiResponse<TicketDetailView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(ticketService.detailForAdmin(id));
    }

    @RequirePermission("service:ticket:handle")
    @PostMapping("/tickets/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable("id") Long id) {
        UserPrincipal principal = AuthContext.require();
        ticketService.accept(id, principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:ticket:assign")
    @PostMapping("/tickets/{id}/assign")
    public ApiResponse<Void> assign(@PathVariable("id") Long id,
                                    @Valid @RequestBody TicketAssignRequest request) {
        UserPrincipal principal = AuthContext.require();
        ticketService.assign(id, request.assigneeId(), principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:ticket:handle")
    @PostMapping("/tickets/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable("id") Long id,
                                    @Valid @RequestBody TicketHandleRequest request) {
        UserPrincipal principal = AuthContext.require();
        ticketService.handle(id, request.content(), principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:ticket:handle")
    @PostMapping("/tickets/{id}/callback")
    public ApiResponse<Void> callback(@PathVariable("id") Long id,
                                      @Valid @RequestBody TicketCallbackRequest request) {
        UserPrincipal principal = AuthContext.require();
        ticketService.callback(id, request.content(), request.result(),
                principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:ticket:handle")
    @PostMapping("/tickets/{id}/finish")
    public ApiResponse<Void> finish(@PathVariable("id") Long id) {
        UserPrincipal principal = AuthContext.require();
        ticketService.finish(id, principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:ticket:close")
    @PostMapping("/tickets/{id}/close")
    public ApiResponse<Void> close(@PathVariable("id") Long id) {
        UserPrincipal principal = AuthContext.require();
        ticketService.close(id, null, principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @RequirePermission("service:complaint:judge")
    @PutMapping("/complaints/{id}/judge")
    public ApiResponse<Void> judge(@PathVariable("id") Long id,
                                   @Valid @RequestBody ComplaintJudgeRequest request) {
        complaintService.judge(id, request.liability(), request.result(), request.punishAction());
        return ApiResponse.ok();
    }
}
