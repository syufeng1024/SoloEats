package com.soloeats.content.job;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.content.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Banner 曝光数回刷：接口只累加 Redis，每 5 分钟由本任务批量写回 t_banner。
 * 多实例用 Redisson 锁互斥，拿不到锁直接跳过本轮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerViewCountJob {

    static final String LOCK_KEY = "lock:content:banner-view-flush";
    private static final long LEASE_SECONDS = 240L;

    private final BannerService bannerService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 */5 * * * ?", zone = "Asia/Shanghai")
    public void flush() {
        try {
            distributedLock.executeWithLock(
                    LOCK_KEY, 0L, LEASE_SECONDS, bannerService::flushViewCounts);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.LOCK_TIMEOUT.getCode()) {
                log.debug("banner view flush skipped, lock held by another instance");
                return;
            }
            log.error("banner view flush failed", ex);
        } catch (RuntimeException ex) {
            log.error("banner view flush failed", ex);
        }
    }
}
