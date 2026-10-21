package com.soloeats.app.debug;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地诊断入口：验证线程池切换时 TraceId 是否保留。
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/debug/trace")
@RequiredArgsConstructor
public class TraceProbeController {

    private final TraceAsyncProbeService traceAsyncProbeService;

    @RequireRole(Roles.ADMIN)
    @GetMapping("/async")
    public ApiResponse<String> async() {
        String traceId = TraceContext.currentTraceId();
        traceAsyncProbeService.logTrace();
        return ApiResponse.ok(traceId);
    }
}
