package com.soloeats.common.log;

/**
 * 切面采集完成后交给持久化端的操作日志快照。
 *
 * <p>切面在请求线程内完成采集（此时 AuthContext / TraceContext 仍有效），
 * 持久化端负责把 tenantId 显式传入异步线程，禁止依赖 ThreadLocal 跨线程传递。
 */
public record OperationLogRecord(
        Long tenantId,
        String traceId,
        String module,
        String action,
        String method,
        String requestUri,
        String requestMethod,
        String requestParams,
        Long operatorId,
        String operatorName,
        String operatorType,
        String ip,
        String ua,
        int resultCode,
        String errorMsg,
        int costMs
) {
}
