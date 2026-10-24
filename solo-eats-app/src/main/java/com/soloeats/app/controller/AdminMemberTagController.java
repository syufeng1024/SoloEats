package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveTagRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.member.service.MemberTagService;
import com.soloeats.member.service.command.SaveTagCommand;
import com.soloeats.member.service.view.TagView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台用户标签分群定义。自动打标规则只落 auto_flag 字段，执行逻辑留 TODO。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/member-tags")
@RequiredArgsConstructor
public class AdminMemberTagController {

    private final MemberTagService memberTagService;

    @RequirePermission("member:tag:list")
    @GetMapping
    public ApiResponse<List<TagView>> list() {
        return ApiResponse.ok(memberTagService.list());
    }

    @RequirePermission("member:tag:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "member", action = "createTag")
    @PostMapping
    public ApiResponse<TagView> create(@Valid @RequestBody SaveTagRequest request) {
        return ApiResponse.ok(memberTagService.create(toCommand(request)));
    }

    @RequirePermission("member:tag:update")
    @OperationLog(module = "member", action = "updateTag")
    @PutMapping("/{id}")
    public ApiResponse<TagView> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody SaveTagRequest request) {
        return ApiResponse.ok(memberTagService.update(id, toCommand(request)));
    }

    private SaveTagCommand toCommand(SaveTagRequest request) {
        return new SaveTagCommand(
                request.name(),
                request.ruleDesc(),
                request.autoFlag(),
                request.status());
    }
}
