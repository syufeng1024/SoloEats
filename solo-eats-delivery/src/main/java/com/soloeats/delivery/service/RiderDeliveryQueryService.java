package com.soloeats.delivery.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.service.view.RiderDeliveryView;

/**
 * 骑手任务只读查询。riderId 由调用方从登录态解析，不接受前端传入。
 */
public interface RiderDeliveryQueryService {

    /**
     * 骑手本人的配送单列表：rider_id 精确匹配，status 非空时精确过滤。
     * 排序 accepted_at DESC，accepted_at 为空时用 created_at 兜底；pageSize 默认 10、上限 50。
     */
    PageResult<RiderDeliveryView> pageMyDeliveries(Long riderId, Integer status,
                                                   int pageNum, int pageSize);

    /**
     * 骑手任务详情：配送单不存在抛 DELIVERY_NOT_FOUND，不属于当前骑手抛 AUTH_FORBIDDEN。
     */
    RiderDeliveryView detail(Long riderId, Long deliveryId);
}
