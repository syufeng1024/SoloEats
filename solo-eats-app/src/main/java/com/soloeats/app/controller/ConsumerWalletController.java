package com.soloeats.app.controller;

import com.soloeats.app.dto.RechargeRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.member.service.MemberWalletService;
import com.soloeats.member.service.query.WalletLogQuery;
import com.soloeats.member.service.view.WalletLogView;
import com.soloeats.member.service.view.WalletView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * C 端钱包。userId 只取令牌，不接受请求体传入。
 */
@RestController
@RequestMapping("/api/v1/c/wallet")
@RequireRole(Roles.C_USER)
@RequiredArgsConstructor
public class ConsumerWalletController {

    private final MemberWalletService memberWalletService;

    @GetMapping
    public ApiResponse<WalletView> wallet() {
        return ApiResponse.ok(memberWalletService.getWallet(currentUserId()));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<WalletLogView>> logs(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        WalletLogQuery query = new WalletLogQuery(bizType, dateFrom, dateTo);
        return ApiResponse.ok(memberWalletService.listLogs(
                currentUserId(), query, pageNum, pageSize));
    }

    /**
     * 防重 key 使用注解默认值（方法签名 + 参数哈希，含 Idempotency-Key），
     * 不能用固定 key：固定 key 会让所有用户共用同一 Redis 键，第二个用户直接被判重复。
     * 业务幂等由 t_member_wallet_log 的 uk_biz(biz_type, biz_id, request_id) 保证。
     */
    @Idempotent(ttlSeconds = 5)
    @PostMapping("/recharge")
    public ApiResponse<WalletView> recharge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RechargeRequest request) {
        return ApiResponse.ok(memberWalletService.recharge(
                currentUserId(), request.amount(), idempotencyKey));
    }

    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
