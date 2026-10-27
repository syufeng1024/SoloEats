package com.soloeats.marketing.job;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.marketing.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 每日 03:00 扫描过期积分批次，生成 source=6 的负数流水。
 * 过期本身由 uk_biz(6, 原流水id) 保证幂等；锁只用于多实例避免重复扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireJob {

    private static final String LOCK_KEY = "lock:marketing:point-expire";
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_SECONDS = 900L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PointService pointService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void expire() {
        LocalDate date = LocalDate.now(ZONE);
        try {
            Integer expired = distributedLock.executeWithLock(
                    LOCK_KEY, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,
                    () -> pointService.expirePoints(date));
            log.info("point expire job finished date={} expired={}", date, expired);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.LOCK_TIMEOUT.getCode()) {
                log.debug("point expire job skipped: lock held by another instance");
                return;
            }
            log.error("point expire job failed date={}", date, ex);
        } catch (Exception ex) {
            log.error("point expire job failed date={}", date, ex);
        }
    }
}
