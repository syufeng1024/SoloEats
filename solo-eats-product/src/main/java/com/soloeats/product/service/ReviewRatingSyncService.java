package com.soloeats.product.service;

import com.soloeats.product.mapper.ProductReviewMapper;
import com.soloeats.product.mapper.ReviewRatingRow;
import com.soloeats.product.service.event.ReviewAuditedEvent;
import com.soloeats.store.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价 → 门店评分的联动同步（T05 卡）。
 *
 * <p>口径（严格按 T05 卡）：评价**通过审核后异步**更新
 * {@code t_shop.rating/rating_taste/rating_package/rating_delivery}，
 * 取**近 90 天**已审核评价的均值；**异步失败不回滚评价**，由定时任务兜底重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewRatingSyncService {

    /** 评分统计窗口（天）。 */
    private static final int RATING_WINDOW_DAYS = 90;

    private final ProductReviewMapper productReviewMapper;
    private final ShopService shopService;

    /**
     * 审核事务提交后异步重算该门店评分。
     *
     * <p>{@code AFTER_COMMIT} 是必须的：若在事务内异步执行，子线程读不到尚未提交的
     * {@code status=1}，会把刚通过的评价漏算。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewAudited(ReviewAuditedEvent event) {
        if (event == null || event.shopId() == null) {
            return;
        }
        syncShopRating(event.shopId());
    }

    /**
     * 重算单个门店评分。异常在这里吞掉——评价已审核通过是既定事实，
     * 不允许因评分同步失败去回滚审核结果。
     *
     * @return 是否实际更新了门店
     */
    public boolean syncShopRating(Long shopId) {
        if (shopId == null || shopId <= 0L) {
            return false;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(RATING_WINDOW_DAYS);
            ReviewRatingRow row = productReviewMapper.ratingOf(shopId, since);
            if (row == null || row.getReviewCount() == null || row.getReviewCount() == 0L) {
                // 窗口内没有已通过评价：保留门店原评分，不清零
                return false;
            }
            boolean updated = shopService.refreshRating(
                    shopId,
                    scale(row.getAvgOverall()),
                    scale(row.getAvgTaste()),
                    scale(row.getAvgPackage()),
                    scale(row.getAvgDelivery()));
            if (!updated) {
                log.warn("review rating sync miss shopId={} (shop not found or deleted)", shopId);
            }
            return updated;
        } catch (RuntimeException ex) {
            log.error("review rating sync failed shopId={}", shopId, ex);
            return false;
        }
    }

    /**
     * 兜底：重算全部有已审核评价的门店。由 {@code ReviewRatingSyncJob} 定时调用。
     *
     * @return 实际更新成功的门店数
     */
    public int syncAllShops() {
        List<Long> shopIds = productReviewMapper.reviewedShopIds();
        if (shopIds == null || shopIds.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Long shopId : shopIds) {
            if (syncShopRating(shopId)) {
                updated++;
            }
        }
        return updated;
    }

    /** t_shop.rating 是 decimal(3,2)，统一保留 2 位四舍五入，避免落库被截断。 */
    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
