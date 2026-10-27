package com.soloeats.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.trace.TraceContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link OperationLog} 切面：采集操作人、URI、耗时、结果码与脱敏参数摘要。
 *
 * <p>三点硬约束：
 * 1. 参数落库前脱敏（password|phone|idCard|bankAccount → ***）并截断到 2000 字符；
 * 2. 持久化走 {@link OperationLogSink} 的 @Async 实现，失败只 log.warn，不影响主流程；
 * 3. 业务异常原样向上抛给 GlobalExceptionHandler，切面不得吞异常。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class OperationLogAspect {

    private static final String LOG_URI_PREFIX = "/api/v1/admin/logs/";
    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final int MAX_UA_LENGTH = 512;
    private static final int MAX_ERROR_LENGTH = 512;

    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\"(?:password|phone|idCard|bankAccount)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

    private final ObjectProvider<OperationLogSink> sinkProvider;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(ObjectProvider<OperationLogSink> sinkProvider, ObjectMapper objectMapper) {
        this.sinkProvider = sinkProvider;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request != null && request.getRequestURI() != null
                && request.getRequestURI().startsWith(LOG_URI_PREFIX)) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        String params = operationLog.recordParams() ? serializeParams(joinPoint.getArgs()) : null;
        try {
            Object result = joinPoint.proceed();
            writeLog(joinPoint, operationLog, request, params, 0, null,
                    (int) (System.currentTimeMillis() - start));
            return result;
        } catch (Throwable ex) {
            int code = ex instanceof BizException bizException
                    ? bizException.getCode()
                    : ErrorCode.SYSTEM_ERROR.getCode();
            writeLog(joinPoint, operationLog, request, params, code, shortMessage(ex),
                    (int) (System.currentTimeMillis() - start));
            // 必须原样抛出：操作日志失败不影响业务，但业务失败不能因切面被吞掉
            throw ex;
        }
    }

    private void writeLog(ProceedingJoinPoint joinPoint,
                          OperationLog operationLog,
                          HttpServletRequest request,
                          String params,
                          int resultCode,
                          String errorMsg,
                          int costMs) {
        try {
            OperationLogSink sink = sinkProvider.getIfAvailable();
            if (sink == null) {
                return;
            }
            UserPrincipal principal = AuthContext.current();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String method = signature.getDeclaringType().getSimpleName()
                    + "#" + signature.getName();
            String traceId = TraceContext.currentTraceId();
            String ip = request == null ? null : clientIp(request);
            String ua = request == null ? null : truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH);

            sink.persist(new OperationLogRecord(
                    principal == null ? null : principal.tenantId(),
                    StringUtils.hasText(traceId) ? traceId : null,
                    operationLog.module(),
                    operationLog.action(),
                    truncate(method, 255),
                    request == null ? null : truncate(request.getRequestURI(), 255),
                    request == null ? null : request.getMethod(),
                    params,
                    principal == null ? null : principal.userId(),
                    principal == null ? null : truncate(principal.username(), 64),
                    resolveOperatorType(principal),
                    ip,
                    ua,
                    resultCode,
                    truncate(errorMsg, MAX_ERROR_LENGTH),
                    Math.max(costMs, 0)
            ));
        } catch (Exception ex) {
            // 日志本身失败不得影响业务
            log.warn("operation log enqueue failed method={} reason={}",
                    joinPoint.getSignature().toShortString(), ex.getMessage());
        }
    }

    private String serializeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> captured = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                captured.add(null);
            } else if (arg instanceof ServletRequest || arg instanceof ServletResponse) {
                captured.add("<servlet>");
            } else if (arg instanceof MultipartFile file) {
                captured.add("<file:" + truncate(file.getOriginalFilename(), 128) + ">");
            } else if (arg instanceof byte[]) {
                captured.add("<bytes>");
            } else {
                captured.add(arg);
            }
        }
        try {
            return sanitizeParams(objectMapper.writeValueAsString(captured));
        } catch (Exception ex) {
            return "\"<unserializable>\"";
        }
    }

    /**
     * 脱敏 + 截断。截断后仍需保持合法 JSON（t_sys_oper_log.request_params 为 json 列），
     * 因此超长时改为 {"truncated":true,"preview":"..."} 结构。
     */
    public static String sanitizeParams(String raw) {
        if (raw == null) {
            return null;
        }
        String masked = SENSITIVE_JSON.matcher(raw).replaceAll("$1\"***\"");
        if (masked.length() <= MAX_PARAMS_LENGTH) {
            return masked;
        }
        String preview = masked.substring(0, MAX_PARAMS_LENGTH - 100)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ");
        return "{\"truncated\":true,\"preview\":\"" + preview + "\"}";
    }

    private static String resolveOperatorType(UserPrincipal principal) {
        if (principal == null || principal.roles() == null || principal.roles().isEmpty()) {
            return "ANONYMOUS";
        }
        Set<String> roles = principal.roles();
        if (roles.stream().anyMatch(role -> role != null && role.startsWith("ADMIN"))) {
            return "ADMIN";
        }
        if (roles.stream().anyMatch(role -> role != null && role.startsWith("MERCHANT"))) {
            return "MERCHANT";
        }
        if (roles.stream().anyMatch(role -> role != null && role.startsWith("RIDER"))) {
            return "RIDER";
        }
        if (roles.contains("C_USER")) {
            return "C_USER";
        }
        return "ANONYMOUS";
    }

    private static String shortMessage(Throwable ex) {
        if (ex == null) {
            return null;
        }
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
