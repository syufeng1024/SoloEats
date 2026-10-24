package com.soloeats.delivery.service;

import com.soloeats.delivery.service.view.DeliveryActionView;
import com.soloeats.delivery.service.view.DispatchCandidate;
import com.soloeats.delivery.service.view.HallOrderView;
import com.soloeats.delivery.service.view.PendingDispatchView;

import java.math.BigDecimal;
import java.util.List;

/**
 * 骑手派单编排：只做校验、查询与事务编排，配送单状态流转全部委托 {@link DeliveryService}。
 */
public interface RiderDispatchService {

    /** 系统派给该骑手的待确认单（status=10），remainingSeconds 为倒计时剩余秒数。 */
    List<PendingDispatchView> pendingDispatches(Long riderId);

    /** 抢单大厅：status=0 且无骑手，按半径过滤后按 distance/fee/time 排序。 */
    List<HallOrderView> hall(Long riderId, BigDecimal lng, BigDecimal lat, String sortBy);

    DeliveryActionView accept(Long deliveryId, Long riderId);

    /** 拒单写 t_dispatch_record 并累加派单次数；达到 max_retry 退回待派单。 */
    void reject(Long deliveryId, Long riderId, String reason);

    DeliveryActionView grab(Long deliveryId, Long riderId);

    /**
     * 接单门槛：认证通过、账号正常、健康证有效、在线、进行中单量未达上限。
     * 任一不满足抛 50006/50009/50007/50008。
     */
    void assertCanTakeOrder(Long riderId);

    /** 按 t_dispatch_config 权重取前 5 名候选骑手，供自动派单使用。 */
    List<DispatchCandidate> rankCandidates(Long deliveryId);
}
