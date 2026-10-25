package com.soloeats.app.vo;

import com.soloeats.delivery.service.view.RiderDeliveryView;

import java.time.LocalDateTime;

/**
 * 骑手任务列表项：配送单字段 + 商品件数。金额单位分。
 */
public record RiderTaskView(
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
        Integer itemCount
) {

    public static RiderTaskView from(RiderDeliveryView view, Integer itemCount) {
        return new RiderTaskView(
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
                itemCount
        );
    }
}
