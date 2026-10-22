package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record OperLogView(
        Long id,
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
        Integer resultCode,
        String errorMsg,
        Integer costMs,
        LocalDateTime createdAt
) {
}
