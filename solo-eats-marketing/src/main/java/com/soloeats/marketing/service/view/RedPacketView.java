package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.RedPacket;

import java.time.LocalDateTime;

/**
 * C 端红包列表项。grabbed 表示当前用户是否已领取过该红包。
 */
public record RedPacketView(
        Long id,
        Integer packetType,
        Long totalAmount,
        Integer totalQty,
        Integer remainQty,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean grabbed
) {

    public static RedPacketView from(RedPacket packet, boolean grabbed) {
        return new RedPacketView(
                packet.getId(),
                packet.getPacketType(),
                packet.getTotalAmount(),
                packet.getTotalQty(),
                packet.getRemainQty(),
                packet.getStartAt(),
                packet.getEndAt(),
                grabbed);
    }
}
