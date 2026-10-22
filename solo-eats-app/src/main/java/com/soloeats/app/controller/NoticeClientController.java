package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.system.service.SysNoticeService;
import com.soloeats.system.service.view.NoticeView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端公告：登录即可访问，不额外要求后台角色。
 */
@RestController
@RequestMapping("/api/v1/c/notices")
@RequiredArgsConstructor
public class NoticeClientController {

    private final SysNoticeService sysNoticeService;

    @GetMapping
    public ApiResponse<PageResult<NoticeView>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(sysNoticeService.pageForUser(
                principal.roles(), pageNum, pageSize));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        AuthContext.require();
        sysNoticeService.markRead(id);
        return ApiResponse.ok();
    }
}
