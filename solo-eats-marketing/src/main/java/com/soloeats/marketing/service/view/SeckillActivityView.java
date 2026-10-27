package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.SeckillActivity;
import com.soloeats.marketing.domain.entity.SeckillItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台秒杀场次详情。status 为按时间窗推导后的业务状态（0待开始 1进行中 2已结束），
 * 不直接透出 DB 中可能滞后的快照值。
 */
public record SeckillActivityView(
        Long id,
        String activityName,
        LocalDateTime sessionStart,
        LocalDateTime sessionEnd,
        LocalDateTime preheatAt,
        Integer status,
        List<ItemView> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record ItemView(
            Long id,
            Long shopId,
            Long spuId,
            Long skuId,
            long seckillPrice,
            int seckillStock,
            int soldQty,
            int remainStock,
            int limitPerUser
    ) {

        public static ItemView from(SeckillItem item) {
            if (item == null) {
                return null;
            }
            int stock = item.getSeckillStock() == null ? 0 : item.getSeckillStock();
            int sold = item.getSoldQty() == null ? 0 : item.getSoldQty();
            return new ItemView(
                    item.getId(),
                    item.getShopId(),
                    item.getSpuId(),
                    item.getSkuId(),
                    item.getSeckillPrice() == null ? 0L : item.getSeckillPrice(),
                    stock,
                    sold,
                    Math.max(stock - sold, 0),
                    item.getLimitPerUser() == null ? 1 : item.getLimitPerUser());
        }
    }

    public static SeckillActivityView from(SeckillActivity activity,
                                           int effectiveStatus,
                                           List<SeckillItem> items) {
        if (activity == null) {
            return null;
        }
        List<ItemView> itemViews = items == null
                ? List.of()
                : items.stream().map(ItemView::from).filter(java.util.Objects::nonNull).toList();
        return new SeckillActivityView(
                activity.getId(),
                activity.getActivityName(),
                activity.getSessionStart(),
                activity.getSessionEnd(),
                activity.getPreheatAt(),
                effectiveStatus,
                itemViews,
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }
}
