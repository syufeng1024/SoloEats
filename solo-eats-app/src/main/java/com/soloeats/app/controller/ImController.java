package com.soloeats.app.controller;

import com.soloeats.app.dto.CreateImSessionRequest;
import com.soloeats.app.dto.SendImMessageRequest;
import com.soloeats.app.security.CurrentReceiver;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.message.service.ImSessionService;
import com.soloeats.message.service.view.ImMessageView;
import com.soloeats.message.service.view.ImSessionView;
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

import java.util.List;

/**
 * 三端共用 IM 会话，登录即可访问。
 *
 * <p>subjectType/subjectId 一律来自 {@link CurrentReceiver}：用户取 userId，
 * 商家取 merchantId，骑手取 riderId。subjectId 必须与 t_im_session.user_id/peer_id
 * 以及 WS 注册 key 同源，否则商家/骑手侧会话与推送都会对不上。
 */
@RestController
@RequestMapping("/api/v1/im/sessions")
@RequiredArgsConstructor
public class ImController {

    private final ImSessionService imSessionService;
    private final CurrentReceiver currentReceiver;

    @GetMapping
    public ApiResponse<List<ImSessionView>> list() {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(imSessionService.listMine(
                currentReceiver.receiverType(principal), currentReceiver.receiverId(principal)));
    }

    @PostMapping
    public ApiResponse<ImSessionView> create(@Valid @RequestBody CreateImSessionRequest request) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(imSessionService.getOrCreate(
                request.orderId(),
                currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal),
                request.peerType(),
                request.peerId()));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ImMessageView>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(required = false) Integer pageSize) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(imSessionService.messages(
                id,
                currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal),
                beforeMessageId,
                pageSize));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ImMessageView> send(@PathVariable Long id,
                                           @Valid @RequestBody SendImMessageRequest request) {
        UserPrincipal principal = currentReceiver.principal();
        return ApiResponse.ok(imSessionService.send(
                id,
                currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal),
                request.msgType(),
                request.content(),
                request.extra() == null ? null : request.extra().toString()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        UserPrincipal principal = currentReceiver.principal();
        imSessionService.markRead(id, currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal));
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> hide(@PathVariable Long id) {
        UserPrincipal principal = currentReceiver.principal();
        imSessionService.hide(id, currentReceiver.receiverType(principal),
                currentReceiver.receiverId(principal));
        return ApiResponse.ok();
    }
}
