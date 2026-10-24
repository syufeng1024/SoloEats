package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveMessageTemplateRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.message.service.MessageTemplateService;
import com.soloeats.message.service.command.SaveMessageTemplateCommand;
import com.soloeats.message.service.query.MessageTemplateQuery;
import com.soloeats.message.service.view.MessageTemplateView;
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

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/message-templates")
@RequiredArgsConstructor
public class AdminMessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    @RequirePermission("message:template:list")
    @GetMapping
    public ApiResponse<PageResult<MessageTemplateView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer templateType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(messageTemplateService.page(
                new MessageTemplateQuery(keyword, templateType, status), pageNum, pageSize));
    }

    @RequirePermission("message:template:create")
    @OperationLog(module = "message", action = "create")
    @PostMapping
    public ApiResponse<MessageTemplateView> create(@Valid @RequestBody SaveMessageTemplateRequest request) {
        return ApiResponse.ok(messageTemplateService.save(toCommand(request)));
    }

    @RequirePermission("message:template:update")
    @OperationLog(module = "message", action = "update")
    @PutMapping("/{id}")
    public ApiResponse<MessageTemplateView> update(@PathVariable Long id,
                                                   @Valid @RequestBody SaveMessageTemplateRequest request) {
        return ApiResponse.ok(messageTemplateService.update(id, toCommand(request)));
    }

    private SaveMessageTemplateCommand toCommand(SaveMessageTemplateRequest request) {
        return new SaveMessageTemplateCommand(
                request.templateCode(),
                request.templateName(),
                request.templateType(),
                request.titleTpl(),
                request.contentTpl(),
                request.channels(),
                request.status());
    }
}
