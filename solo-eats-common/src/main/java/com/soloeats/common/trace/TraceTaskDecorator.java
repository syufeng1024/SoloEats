package com.soloeats.common.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 线程池任务切换时复制 TraceId 与 MDC，避免异步线程丢日志上下文。
 */
public class TraceTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        String traceId = TraceContext.currentTraceId();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                if (StringUtils.hasText(traceId)) {
                    TraceContext.setTraceId(traceId);
                }
                runnable.run();
            } finally {
                MDC.clear();
                TraceContext.clear();
            }
        };
    }
}
