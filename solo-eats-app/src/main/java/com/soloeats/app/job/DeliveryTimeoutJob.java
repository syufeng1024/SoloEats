package com.soloeats.app.job;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.delivery.service.DeliveryTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟扫描一次配送超时：status=40 且 promise_deliver_at 已过、未妥投的配送单，
 * 每单只写一条 type=1 的骑手异常记录。多实例用 Redisson 锁互斥。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutJob {

    static final String LOCK_KEY = "lock:delivery:timeout-scan";
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_SECONDS = 55L;

    private final DeliveryTimeoutService deliveryTimeoutService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 * * * * ?")
    public void scan() {
        try {
            distributedLock.executeWithLock(LOCK_KEY, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,
                    deliveryTimeoutService::markTimeouts);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.LOCK_TIMEOUT.getCode()) {
                log.debug("delivery timeout scan skipped, lock held by another instance");
                return;
            }
            log.error("delivery timeout scan failed", ex);
        } catch (RuntimeException ex) {
            log.error("delivery timeout scan failed", ex);
        }
    }
}
