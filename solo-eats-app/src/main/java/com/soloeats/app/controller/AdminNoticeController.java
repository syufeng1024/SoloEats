package com.soloeats.app.controller;

import com.soloeats.app.dto.NoticePublishRequest;
import com.soloeats.app.dto.SaveNoticeRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.system.service.SysNoticeService;
import com.soloeats.system.service.command.SaveNoticeCommand;
import com.soloeats.system.service.view.NoticeView;
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
@RequestMapping("/api/v1/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final SysNoticeService sysNoticeService;

    @RequirePermission("system:notice:list")
    @GetMapping
    public ApiResponse<PageResult<NoticeView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Integer targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysNoticeService.page(
                status, noticeType, targetType, keyword, pageNum, pageSize));
    }

    @RequirePermission("system:notice:create")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "公告管理", action = "新增")
    @PostMapping
    public ApiResponse<NoticeView> create(@Valid @RequestBody SaveNoticeRequest request) {
        return ApiResponse.ok(sysNoticeService.create(toCommand(request)));
    }

    @RequirePermission("system:notice:update")
    @OperationLog(module = "公告管理", action = "修改")
    @PutMapping("/{id}")
    public ApiResponse<NoticeView> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveNoticeRequest request) {
        return ApiResponse.ok(sysNoticeService.update(id, toCommand(request)));
    }

    @RequirePermission("system:notice:update")
    @OperationLog(module = "公告管理", action = "发布或撤回")
    @PutMapping("/{id}/publish")
    public ApiResponse<Void> publish(
            @PathVariable Long id,
            @Valid @RequestBody NoticePublishRequest request) {
        sysNoticeService.publish(id, request.status());
        return ApiResponse.ok();
    }

    private SaveNoticeCommand toCommand(SaveNoticeRequest request) {
        return new SaveNoticeCommand(
                request.title(),
                request.content(),
                request.noticeType(),
                request.targetType(),
                request.publishAt(),
                request.expireAt());
    }
}
