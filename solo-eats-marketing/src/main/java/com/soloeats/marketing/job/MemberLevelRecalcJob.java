package com.soloeats.marketing.job;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.marketing.service.MemberLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 季度首日 02:00 全量重算会员等级。
 * 多实例下用 Redis 锁保证只有一个实例执行；抢不到锁直接跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberLevelRecalcJob {

    private static final String LOCK_KEY = "lock:marketing:member-level-recalc";
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_SECONDS = 1800L;

    private final MemberLevelService memberLevelService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 0 2 1 1/3 ?", zone = "Asia/Shanghai")
    public void recalc() {
        try {
            Integer changed = distributedLock.executeWithLock(
                    LOCK_KEY, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,
                    () -> memberLevelService.recalcAll());
            log.info("member level recalc job finished changed={}", changed);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.LOCK_TIMEOUT.getCode()) {
                log.debug("member level recalc job skipped: lock held by another instance");
                return;
            }
            log.error("member level recalc job failed", ex);
        } catch (Exception ex) {
            // 调度线程不能因为单次任务异常而退出
            log.error("member level recalc job failed", ex);
        }
    }
}
