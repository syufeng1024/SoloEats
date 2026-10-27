package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.SeckillActivity;
import com.soloeats.marketing.domain.entity.SeckillItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端秒杀场次。只暴露用户选购所需字段，不含 shop_id / sold_qty / version 等内部字段；
 * remainStock 为 seckill_stock - sold_qty，下限取 0，防止展示负数。
 */
public record SeckillSessionView(
        Long id,
        String activityName,
        LocalDateTime sessionStart,
        LocalDateTime sessionEnd,
        LocalDateTime preheatAt,
        Integer status,
        List<ItemView> items
) {

    public record ItemView(
            Long itemId,
            Long skuId,
            Long spuId,
            long seckillPrice,
            int seckillStock,
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
                    item.getSkuId(),
                    item.getSpuId(),
                    item.getSeckillPrice() == null ? 0L : item.getSeckillPrice(),
                    stock,
                    Math.max(stock - sold, 0),
                    item.getLimitPerUser() == null ? 1 : item.getLimitPerUser());
        }
    }

    public static SeckillSessionView from(SeckillActivity activity,
                                          int effectiveStatus,
                                          List<SeckillItem> items) {
        if (activity == null) {
            return null;
        }
        List<ItemView> itemViews = items == null
                ? List.of()
                : items.stream().map(ItemView::from).filter(java.util.Objects::nonNull).toList();
        return new SeckillSessionView(
                activity.getId(),
                activity.getActivityName(),
                activity.getSessionStart(),
                activity.getSessionEnd(),
                activity.getPreheatAt(),
                effectiveStatus,
                itemViews);
    }
}
