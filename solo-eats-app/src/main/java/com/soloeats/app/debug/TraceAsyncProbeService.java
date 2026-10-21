package com.soloeats.app.debug;

import com.soloeats.common.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TraceAsyncProbeService {

    @Async
    public void logTrace() {
        log.info("async trace probe traceId={}", TraceContext.currentTraceId());
    }
}
