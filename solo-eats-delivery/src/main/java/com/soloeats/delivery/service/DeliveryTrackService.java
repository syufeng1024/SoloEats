package com.soloeats.delivery.service;

import com.soloeats.delivery.service.command.TrackPointCommand;
import com.soloeats.delivery.service.view.DeliveryTrackView;
import com.soloeats.delivery.service.view.TrackPointView;

import java.util.List;

/**
 * 配送轨迹上报与查询。上报只落轨迹表并刷新 Redis 位置，不改配送单状态。
 */
public interface DeliveryTrackService {

    /**
     * 批量上报轨迹点：单次最多 20 点，所有 deliveryId 必须归属该骑手。
     * 多行 INSERT 一次落库，并用最后一个点刷新 {@code rider:online:{riderId}}。
     */
    void report(Long riderId, List<TrackPointCommand> points);

    /** 最近 limit 个点按 report_at 升序；limit<=0 时取 100，上限 1000。 */
    List<TrackPointView> queryByDelivery(Long deliveryId, int limit);

    /** 最新一个轨迹点，无数据返回 null。 */
    TrackPointView latestPosition(Long deliveryId);

    /** 后台回放：全量轨迹按 report_at 升序。 */
    List<TrackPointView> replay(Long deliveryId);

    /** C 端：最近 100 点 + 骑手 Redis 实时位置（不含手机号等隐私）。 */
    DeliveryTrackView trackForConsumer(Long deliveryId);
}
