package com.soloeats.common.trace;

import org.slf4j.MDC;

/**
 * 当前请求/任务的 TraceId。Filter 写入，MQ 消费与线程池切换通过 open() 恢复。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = TRACE_ID.get();
        return traceId == null ? "" : traceId;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    public static TraceScope open(String traceId) {
        String previous = TRACE_ID.get();
        String value = traceId == null ? "" : traceId;
        TRACE_ID.set(value);
        MDC.put(MDC_KEY, value);
        return () -> {
            if (previous == null) {
                TRACE_ID.remove();
                MDC.remove(MDC_KEY);
            } else {
                TRACE_ID.set(previous);
                MDC.put(MDC_KEY, previous);
            }
        };
    }

    public interface TraceScope extends AutoCloseable {
        @Override
        void close();
    }
}
