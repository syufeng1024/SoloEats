package com.soloeats.app.vo;

import com.soloeats.delivery.service.view.RiderDeliveryView;
import com.soloeats.order.service.view.OrderItemBriefView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 骑手任务详情：配送单字段 + 商品件数 + 商品明细。金额单位分。
 */
public record RiderTaskDetailView(
        Long deliveryId,
        String deliveryNo,
        Long orderId,
        Long shopId,
        Integer status,
        String statusName,
        String pickupAddress,
        String receiverName,
        String receiverAddress,
        String receiverPhoneMask,
        Integer distanceM,
        Long deliveryFee,
        LocalDateTime promiseDeliverAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime dispatchExpireAt,
        Integer itemCount,
        List<OrderItemBriefView> items
) {

    public static RiderTaskDetailView from(RiderDeliveryView view, Integer itemCount,
                                           List<OrderItemBriefView> items) {
        return new RiderTaskDetailView(
                view.deliveryId(),
                view.deliveryNo(),
                view.orderId(),
                view.shopId(),
                view.status(),
                view.statusName(),
                view.pickupAddress(),
                view.receiverName(),
                view.receiverAddress(),
                view.receiverPhoneMask(),
                view.distanceM(),
                view.deliveryFee(),
                view.promiseDeliverAt(),
                view.acceptedAt(),
                view.pickedUpAt(),
                view.deliveredAt(),
                view.dispatchExpireAt(),
                itemCount,
                items
        );
    }
}
