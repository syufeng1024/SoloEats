package com.soloeats.app.controller;

import com.soloeats.app.security.CurrentReceiver;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
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
 * 当前登录者的消息中心。receiverType 由 roles 推断，
 * receiverId 由 {@link CurrentReceiver#receiverId(UserPrincipal)} 解析为业务主体 ID
 * （用户=userId、商家=merchantId、骑手=riderId），不接受前端传参，天然杜绝越权查他人消息。
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final SiteMessageService siteMessageService;
    private final CurrentReceiver currentReceiver;

    @GetMapping
    public ApiResponse<PageResult<SiteMessageView>> page(
            @RequestParam(required = false) Integer msgType,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(siteMessageService.page(
                currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal),
                new SiteMessageQuery(msgType, readFlag),
                pageNum,
                pageSize));
    }

    /**
     * 站内信详情。receiverType/receiverId 与 markRead 同一口径，
     * 非本人消息由 service 抛 10004，不接受前端传 receiver 参数。
     */
    @RequireRole({Roles.C_USER, Roles.MERCHANT, Roles.RIDER, Roles.ADMIN})
    @GetMapping("/{id}")
    public ApiResponse<SiteMessageView> detail(@PathVariable("id") Long id) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(siteMessageService.detail(id,
                currentReceiver.receiverType(principal), currentReceiver.receiverId(principal)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount() {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(siteMessageService.unreadCount(
                currentReceiver.receiverType(principal), currentReceiver.receiverId(principal)));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        UserPrincipal principal = currentReceiver.principal();
        siteMessageService.markRead(id, currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal));
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@RequestParam(required = false) Integer msgType) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(siteMessageService.markAllRead(
                currentReceiver.receiverType(principal), currentReceiver.receiverId(principal), msgType));
    }
}
