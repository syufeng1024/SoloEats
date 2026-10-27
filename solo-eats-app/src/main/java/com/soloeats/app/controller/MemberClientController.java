package com.soloeats.app.controller;

import com.soloeats.app.dto.CompensateCheckinRequest;
import com.soloeats.app.dto.InviteBindRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.marketing.service.CheckinService;
import com.soloeats.marketing.service.InviteService;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.service.RedPacketService;
import com.soloeats.marketing.service.view.CheckinResultView;
import com.soloeats.marketing.service.view.CheckinStatusView;
import com.soloeats.marketing.service.view.GrowthLogView;
import com.soloeats.marketing.service.view.MemberProfileView;
import com.soloeats.marketing.service.view.PointAccountView;
import com.soloeats.marketing.service.view.PointLogView;
import com.soloeats.marketing.service.view.RedPacketView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final CheckinService checkinService;
    private final InviteService inviteService;
    private final RedPacketService redPacketService;

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

    // ---------------------------------------------------------------
    // 签到
    // ---------------------------------------------------------------

    /**
     * 今日签到。不加 @Idempotent：同日重复必须返回 40207，
     * 而幂等切面会先返回 40004；业务幂等由 uk_user_date 保证。
     */
    @PostMapping("/checkin")
    public ApiResponse<CheckinResultView> checkin() {
        return ApiResponse.ok(checkinService.checkin(currentUserId()));
    }

    @PostMapping("/checkin/compensate")
    public ApiResponse<CheckinResultView> compensate(
            @Valid @RequestBody CompensateCheckinRequest request) {
        return ApiResponse.ok(checkinService.compensate(currentUserId(), request.date()));
    }

    @GetMapping("/checkin/status")
    public ApiResponse<CheckinStatusView> checkinStatus() {
        return ApiResponse.ok(checkinService.status(currentUserId()));
    }

    // ---------------------------------------------------------------
    // 邀请
    // ---------------------------------------------------------------

    @GetMapping("/invite/code")
    public ApiResponse<String> inviteCode() {
        return ApiResponse.ok(inviteService.inviteCode(currentUserId()));
    }

    @PostMapping("/invite/bind")
    public ApiResponse<Void> bindInvite(@Valid @RequestBody InviteBindRequest request) {
        inviteService.bind(currentUserId(), request.inviteCode());
        return ApiResponse.ok();
    }

    // ---------------------------------------------------------------
    // 红包
    // ---------------------------------------------------------------

    @GetMapping("/red-packets")
    public ApiResponse<List<RedPacketView>> redPackets() {
        return ApiResponse.ok(redPacketService.listAvailable(currentUserId()));
    }

    @PostMapping("/red-packets/{id}/grab")
    public ApiResponse<Long> grabRedPacket(@PathVariable("id") Long id) {
        return ApiResponse.ok(redPacketService.grab(id, currentUserId()));
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
