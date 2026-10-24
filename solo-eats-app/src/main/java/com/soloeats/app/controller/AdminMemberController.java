package com.soloeats.app.controller;

import com.soloeats.app.dto.AdjustWalletRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.member.service.MemberWalletService;
import com.soloeats.member.service.query.WalletLogQuery;
import com.soloeats.member.service.view.WalletLogView;
import com.soloeats.member.service.view.WalletView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台会员管理。T06 阶段C-1 只提供钱包流水与调账两个端点，
 * 会员列表/详情/状态由阶段C-2 继续追加，不要抢写。
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequireRole(Roles.ADMIN)
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberWalletService memberWalletService;

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
