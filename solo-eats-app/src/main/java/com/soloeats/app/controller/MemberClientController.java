package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.service.view.GrowthLogView;
import com.soloeats.marketing.service.view.MemberProfileView;
import com.soloeats.marketing.service.view.PointAccountView;
import com.soloeats.marketing.service.view.PointLogView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端会员等级/成长值/积分。userId 只取令牌，不接受请求参数传入。
 */
@RequireRole(Roles.C_USER)
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class MemberClientController {

    private final MemberLevelService memberLevelService;
    private final PointService pointService;

    @GetMapping("/member/profile")
    public ApiResponse<MemberProfileView> profile() {
        return ApiResponse.ok(memberLevelService.profile(currentUserId()));
    }

    @GetMapping("/member/growth-logs")
    public ApiResponse<List<GrowthLogView>> growthLogs(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(memberLevelService.growthLogs(
                currentUserId(), pageNum, pageSize));
    }

    @GetMapping("/points")
    public ApiResponse<PointAccountView> points() {
        return ApiResponse.ok(pointService.account(currentUserId()));
    }

    @GetMapping("/points/logs")
    public ApiResponse<PageResult<PointLogView>> pointLogs(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(pointService.logs(currentUserId(), pageNum, pageSize));
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
