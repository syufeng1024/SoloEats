package com.soloeats.report.job;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.report.service.ReportAggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 报表聚合调度。
 *
 * <p>每日 02:30 聚合前一日：必须晚于商家/骑手账单 02:00 的生成时间，否则佣金与结算支出缺数。
 * 每 5 分钟刷新当日聚合行，供工作台与经营总览读取；多实例用 Redisson 锁互斥。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAggregateJob {

    static final String DAILY_LOCK_KEY = "lock:report:daily-aggregate";
    static final String TODAY_LOCK_KEY = "lock:report:today-refresh";

    private static final long DAILY_LEASE_SECONDS = 1800L;
    private static final long TODAY_LEASE_SECONDS = 240L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ReportAggregateService reportAggregateService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 30 2 * * ?", zone = "Asia/Shanghai")
    public void aggregateYesterday() {
        LocalDate statDate = LocalDate.now(ZONE).minusDays(1);
        runQuietly(DAILY_LOCK_KEY, DAILY_LEASE_SECONDS, "daily",
                () -> reportAggregateService.aggregate(statDate));
    }

    @Scheduled(cron = "0 */5 * * * ?", zone = "Asia/Shanghai")
    public void refreshToday() {
        LocalDate statDate = LocalDate.now(ZONE);
        runQuietly(TODAY_LOCK_KEY, TODAY_LEASE_SECONDS, "today",
                () -> reportAggregateService.aggregate(statDate));
    }

    private void runQuietly(String lockKey, long leaseSeconds, String name, Runnable action) {
        try {
            distributedLock.executeWithLock(lockKey, 0L, leaseSeconds, action);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.LOCK_TIMEOUT.getCode()) {
                log.debug("report {} aggregate skipped, lock held by another instance", name);
                return;
            }
            log.error("report {} aggregate failed", name, ex);
        } catch (RuntimeException ex) {
            log.error("report {} aggregate failed", name, ex);
        }
    }
}
