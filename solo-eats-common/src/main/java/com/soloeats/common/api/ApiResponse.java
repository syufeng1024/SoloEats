package com.soloeats.common.api;

import com.soloeats.common.trace.TraceContext;

/**
 * 统一响应结构：{ code, message, data, traceId }。
 * code = 0 表示成功，其余为业务错误码。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, TraceContext.currentTraceId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null, TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceContext.currentTraceId());
    }
}
