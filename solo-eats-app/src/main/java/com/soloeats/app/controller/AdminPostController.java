package com.soloeats.app.controller;

import com.soloeats.app.dto.SavePostRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.system.service.SysOrgService;
import com.soloeats.system.service.command.SavePostCommand;
import com.soloeats.system.service.view.PostView;
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

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final SysOrgService sysOrgService;

    @RequirePermission("system:post:list")
    @GetMapping
    public ApiResponse<PageResult<PostView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysOrgService.postPage(status, keyword, pageNum, pageSize));
    }

    @RequirePermission("system:post:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping
    public ApiResponse<PostView> create(@Valid @RequestBody SavePostRequest request) {
        return ApiResponse.ok(sysOrgService.createPost(toCommand(request)));
    }

    @RequirePermission("system:post:update")
    @PutMapping("/{id}")
    public ApiResponse<PostView> update(
            @PathVariable Long id,
            @Valid @RequestBody SavePostRequest request) {
        return ApiResponse.ok(sysOrgService.updatePost(id, toCommand(request)));
    }

    @RequirePermission("system:post:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysOrgService.deletePost(id);
        return ApiResponse.ok();
    }

    private SavePostCommand toCommand(SavePostRequest request) {
        return new SavePostCommand(
                request.postCode(),
                request.postName(),
                request.sort(),
                request.status(),
                request.remark());
    }
}
