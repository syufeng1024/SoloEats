package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveQuickReplyRequest;
import com.soloeats.app.dto.SendSessionMessageRequest;
import com.soloeats.app.dto.TransferSessionRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.message.domain.enums.ReceiverType;
import com.soloeats.message.service.ImSessionService;
import com.soloeats.message.service.view.AgentSessionView;
import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.service.service.CsSessionService;
import com.soloeats.service.service.QuickReplyService;
import com.soloeats.service.service.view.CsStatsView;
import com.soloeats.service.service.view.QuickReplyView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 客服工作台坐席端（接口 21~27）。权限码由 V28 种入 t_sys_menu（id 146~147）。
 *
 * <p>坐席 ID 一律取 JWT 里的 {@code principal.userId()}：平台账号的 t_sys_user.id 与
 * t_im_session.agent_id 同源，不能混用 merchantId/riderId。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/cs")
@RequiredArgsConstructor
public class AdminCsController {

    private final CsSessionService csSessionService;
    private final QuickReplyService quickReplyService;
    private final ImSessionService imSessionService;

    // ------------------------------------------------------------ 会话接待

    @RequirePermission("service:cs:list")
    @GetMapping("/sessions")
    public ApiResponse<PageResult<AgentSessionView>> sessions(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(csSessionService.listSessions(status, agentId, pageNum, pageSize));
    }

    @RequirePermission("service:cs:handle")
    @PostMapping("/sessions/{id}/take")
    public ApiResponse<Void> take(@PathVariable("id") Long id) {
        UserPrincipal principal = AuthContext.require();
        csSessionService.take(id, principal.userId());
        return ApiResponse.ok();
    }

    @RequirePermission("service:cs:handle")
    @PostMapping("/sessions/{id}/transfer")
    public ApiResponse<Void> transfer(@PathVariable("id") Long id,
                                      @Valid @RequestBody TransferSessionRequest request) {
        UserPrincipal principal = AuthContext.require();
        csSessionService.transfer(id, request.toAgentId(), principal.userId());
        return ApiResponse.ok();
    }

    @RequirePermission("service:cs:handle")
    @PostMapping("/sessions/{id}/close")
    public ApiResponse<Void> close(@PathVariable("id") Long id) {
        UserPrincipal principal = AuthContext.require();
        csSessionService.close(id, principal.userId());
        return ApiResponse.ok();
    }

    /**
     * 会话记录。按 agent_id 判权（agentMessages）：待接待会话任意坐席可读，
     * 已被他人接待的会话仅接待坐席可读，其余抛 90003。
     */
    @RequirePermission("service:cs:list")
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ImMessageView>> messages(
            @PathVariable("id") Long id,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(required = false) Integer pageSize) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(imSessionService.agentMessages(
                id, principal.userId(), beforeMessageId, pageSize));
    }

    /**
     * 坐席端发送消息。判权在 ImSessionService#send：客服会话按 agent_id 认定参与者，
     * 待接待会话（agent_id 为空）仍返回 90003，必须先接待再发言。
     */
    @RequirePermission("service:cs:handle")
    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ImMessageView> sendMessage(
            @PathVariable("id") Long id,
            @Valid @RequestBody SendSessionMessageRequest request) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(imSessionService.send(
                id, ReceiverType.PLATFORM.getCode(), principal.userId(),
                request.msgType(), request.content(), request.extraJson()));
    }

    // ------------------------------------------------------------ 快捷话术

    @RequirePermission("service:cs:list")
    @GetMapping("/quick-replies")
    public ApiResponse<List<QuickReplyView>> quickReplies(
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(quickReplyService.list(category));
    }

    @RequirePermission("service:cs:handle")
    @PostMapping("/quick-replies")
    public ApiResponse<Long> createQuickReply(@Valid @RequestBody SaveQuickReplyRequest request) {
        return ApiResponse.ok(quickReplyService.create(request.toCommand()));
    }

    @RequirePermission("service:cs:handle")
    @PutMapping("/quick-replies/{id}")
    public ApiResponse<Void> updateQuickReply(@PathVariable("id") Long id,
                                              @Valid @RequestBody SaveQuickReplyRequest request) {
        quickReplyService.update(id, request.toCommand());
        return ApiResponse.ok();
    }

    @RequirePermission("service:cs:handle")
    @DeleteMapping("/quick-replies/{id}")
    public ApiResponse<Void> deleteQuickReply(@PathVariable("id") Long id) {
        quickReplyService.delete(id);
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------ 坐席统计

    /**
     * 会话接待统计。agentId 不传时统计全平台；日期范围两端都含当天。
     * 该口径独立于 /admin/tickets/stats（工单统计），不做合并。
     */
    @RequirePermission("service:cs:list")
    @GetMapping("/stats")
    public ApiResponse<CsStatsView> stats(
            @RequestParam(required = false) Long agentId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(csSessionService.stats(agentId, dateFrom, dateTo));
    }
}
