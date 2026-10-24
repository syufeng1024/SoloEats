package com.soloeats.app.controller;

import com.soloeats.app.security.CurrentReceiver;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.message.service.SiteMessageService;
import com.soloeats.message.service.query.SiteMessageQuery;
import com.soloeats.message.service.view.SiteMessageView;
import com.soloeats.message.service.view.UnreadCountView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录者的消息中心。receiverType 由 roles 推断，receiverId 取 AuthContext.userId()，
 * 不接受前端传参，天然杜绝越权查他人消息。
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final SiteMessageService siteMessageService;

    @GetMapping
    public ApiResponse<PageResult<SiteMessageView>> page(
            @RequestParam(required = false) Integer msgType,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        UserPrincipal principal = CurrentReceiver.principal();
        return ApiResponse.ok(siteMessageService.page(
                CurrentReceiver.receiverType(principal),
                principal.userId(),
                new SiteMessageQuery(msgType, readFlag),
                pageNum,
                pageSize));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount() {
        UserPrincipal principal = CurrentReceiver.principal();
        return ApiResponse.ok(siteMessageService.unreadCount(
                CurrentReceiver.receiverType(principal), principal.userId()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        UserPrincipal principal = CurrentReceiver.principal();
        siteMessageService.markRead(id, CurrentReceiver.receiverType(principal), principal.userId());
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@RequestParam(required = false) Integer msgType) {
        UserPrincipal principal = CurrentReceiver.principal();
        return ApiResponse.ok(siteMessageService.markAllRead(
                CurrentReceiver.receiverType(principal), principal.userId(), msgType));
    }
}
