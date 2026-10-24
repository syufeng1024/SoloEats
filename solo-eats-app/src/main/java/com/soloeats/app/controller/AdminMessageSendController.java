package com.soloeats.app.controller;

import com.soloeats.app.dto.SendSiteMessageRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.message.service.SiteMessageService;
import com.soloeats.message.service.command.SendMessageCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 后台定向发送站内信。逐条渲染落库，返回成功条数；
 * 单条失败直接抛出，由 GlobalExceptionHandler 转成对应错误码，不做静默跳过。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/messages")
@RequiredArgsConstructor
public class AdminMessageSendController {

    private final SiteMessageService siteMessageService;

    @RequirePermission("message:send")
    @OperationLog(module = "message", action = "send")
    @PostMapping("/send")
    public ApiResponse<Integer> send(@Valid @RequestBody SendSiteMessageRequest request) {
        Map<String, String> params = request.params() == null ? Map.of() : request.params();
        // 去重后按提交顺序发送，避免同一请求重复投递
        List<Long> receiverIds = List.copyOf(new LinkedHashSet<>(request.receiverIds()));
        int sent = 0;
        for (Long receiverId : receiverIds) {
            siteMessageService.send(new SendMessageCommand(
                    request.receiverType(),
                    receiverId,
                    request.templateCode(),
                    params,
                    null,
                    null,
                    null));
            sent++;
        }
        return ApiResponse.ok(sent);
    }
}
