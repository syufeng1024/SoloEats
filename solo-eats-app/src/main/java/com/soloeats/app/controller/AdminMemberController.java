package com.soloeats.app.controller;

import com.soloeats.app.dto.AdjustWalletRequest;
import com.soloeats.app.dto.AssignTagsRequest;
import com.soloeats.app.dto.UpdateMemberStatusRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.member.service.MemberAdminService;
import com.soloeats.member.service.MemberTagService;
import com.soloeats.member.service.MemberWalletService;
import com.soloeats.member.service.query.MemberQuery;
import com.soloeats.member.service.query.WalletLogQuery;
import com.soloeats.member.service.view.AdminAddressView;
import com.soloeats.member.service.view.MemberDetailView;
import com.soloeats.member.service.view.MemberView;
import com.soloeats.member.service.view.WalletLogView;
import com.soloeats.member.service.view.WalletView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台会员管理：钱包流水/调账（阶段C-1）+ 会员列表/详情/状态/地址/打标（阶段C-2）。
 * 手机号一律返回掩码，任何端点都不返回明文。
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequireRole(Roles.ADMIN)
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberWalletService memberWalletService;
    private final MemberAdminService memberAdminService;
    private final MemberTagService memberTagService;

    @RequirePermission("member:user:list")
    @GetMapping
    public ApiResponse<PageResult<MemberView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long levelId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(memberAdminService.page(
                new MemberQuery(status, keyword, levelId), pageNum, pageSize));
    }

    @RequirePermission("member:user:detail")
    @GetMapping("/{id}")
    public ApiResponse<MemberDetailView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(memberAdminService.detail(id));
    }

    @RequirePermission("member:user:update")
    @OperationLog(module = "member", action = "updateStatus")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody UpdateMemberStatusRequest request) {
        memberAdminService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @RequirePermission("member:user:detail")
    @GetMapping("/{id}/addresses")
    public ApiResponse<List<AdminAddressView>> addresses(@PathVariable("id") Long id) {
        return ApiResponse.ok(memberAdminService.listAddresses(id));
    }

    @RequirePermission("member:tag:assign")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "member", action = "assignTags")
    @PostMapping("/{id}/tags")
    public ApiResponse<Void> assignTags(@PathVariable("id") Long id,
                                        @Valid @RequestBody AssignTagsRequest request) {
        memberTagService.assignTags(id, request.tagIds());
        return ApiResponse.ok();
    }

    @RequirePermission("member:wallet:list")
    @GetMapping("/{id}/wallet/logs")
    public ApiResponse<PageResult<WalletLogView>> walletLogs(
            @PathVariable("id") Long id,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        WalletLogQuery query = new WalletLogQuery(bizType, dateFrom, dateTo);
        return ApiResponse.ok(memberWalletService.listLogs(id, query, pageNum, pageSize));
    }

    @RequirePermission("member:wallet:adjust")
    @OperationLog(module = "member", action = "adjust")
    @Idempotent(ttlSeconds = 5)
    @PostMapping("/{id}/wallet/adjust")
    public ApiResponse<WalletView> adjust(@PathVariable("id") Long id,
                                          @Valid @RequestBody AdjustWalletRequest request) {
        UserPrincipal principal = AuthContext.require();
        return ApiResponse.ok(memberWalletService.adjust(
                id,
                request.amount(),
                request.reason(),
                request.requestId(),
                principal.userId(),
                operatorName(principal)));
    }

    private String operatorName(UserPrincipal principal) {
        if (principal.username() != null && !principal.username().isBlank()) {
            return principal.username();
        }
        return "admin-" + principal.userId();
    }
}
