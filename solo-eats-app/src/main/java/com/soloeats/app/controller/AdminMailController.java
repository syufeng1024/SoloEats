package com.soloeats.app.controller;

import com.soloeats.app.dto.MailTestRequest;
import com.soloeats.app.dto.SaveMailAccountRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.system.service.SysMailService;
import com.soloeats.system.service.command.SaveMailAccountCommand;
import com.soloeats.system.service.view.MailAccountView;
import com.soloeats.system.service.view.MailLogView;
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

import java.util.List;

@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/mails")
@RequiredArgsConstructor
public class AdminMailController {

    private final SysMailService sysMailService;

    @RequirePermission("system:mail:list")
    @GetMapping("/accounts")
    public ApiResponse<List<MailAccountView>> listAccounts() {
        return ApiResponse.ok(sysMailService.listAccounts());
    }

    @RequirePermission("system:mail:update")
    @Idempotent(ttlSeconds = 60)
    @OperationLog(module = "邮件管理", action = "新增账号", recordParams = false)
    @PostMapping("/accounts")
    public ApiResponse<MailAccountView> createAccount(
            @Valid @RequestBody SaveMailAccountRequest request) {
        return ApiResponse.ok(sysMailService.createAccount(toCommand(request)));
    }

    @RequirePermission("system:mail:update")
    @OperationLog(module = "邮件管理", action = "修改账号", recordParams = false)
    @PutMapping("/accounts/{id}")
    public ApiResponse<MailAccountView> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody SaveMailAccountRequest request) {
        return ApiResponse.ok(sysMailService.updateAccount(id, toCommand(request)));
    }

    @RequirePermission("system:mail:test")
    @Idempotent(ttlSeconds = 30)
    @OperationLog(module = "邮件管理", action = "发送测试")
    @PostMapping("/test")
    public ApiResponse<Void> sendTest(@Valid @RequestBody MailTestRequest request) {
        sysMailService.sendTest(request.accountId(), request.toAddr());
        return ApiResponse.ok();
    }

    @RequirePermission("system:mail:list")
    @GetMapping("/logs")
    public ApiResponse<PageResult<MailLogView>> listLogs(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysMailService.pageLogs(status, pageNum, pageSize));
    }

    private SaveMailAccountCommand toCommand(SaveMailAccountRequest request) {
        return new SaveMailAccountCommand(
                request.accountName(),
                request.host(),
                request.port(),
                request.username(),
                request.password(),
                request.fromAddr(),
                request.ssl(),
                request.status());
    }
}
