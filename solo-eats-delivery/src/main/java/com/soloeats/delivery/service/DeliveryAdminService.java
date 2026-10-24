package com.soloeats.delivery.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.delivery.service.command.DeliveryQuery;
import com.soloeats.delivery.service.view.DeliveryOrderView;

/**
 * 平台侧配送单只读列表与改派。
 */
public interface DeliveryAdminService {

    PageResult<DeliveryOrderView> page(DeliveryQuery query, long pageNum, long pageSize);

    /**
     * 改派：riderId 为当前（原）骑手，用于校验释放对象。
     * 释放后配送单回到 0 待派单、rider_id 与派单倒计时清空，并写一条手工派单记录（type=3，status=4 取消）。
     * 不改 order_id / delivery_no。
     */
    void reassign(Long deliveryId, Long riderId, String reason, Long operatorId);
}
