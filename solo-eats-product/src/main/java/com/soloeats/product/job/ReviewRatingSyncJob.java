package com.soloeats.product.job;

import com.soloeats.product.service.ReviewRatingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 门店评分兜底重算（T05 卡：「异步失败不回滚评价，靠定时任务兜底」）。
 *
 * <p>默认每日 03:30 全量重算一次。审核时的异步同步若因进程重启、线程池饱和、
 * DB 瞬时不可用等原因没跑成，最迟次日会自愈。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRatingSyncJob {

    private final ReviewRatingSyncService reviewRatingSyncService;

    @Scheduled(cron = "${app.review.rating-sync-cron:0 30 3 * * ?}")
    public void run() {
        int updated = reviewRatingSyncService.syncAllShops();
        log.info("review rating sync job finished shops={}", updated);
    }
}
