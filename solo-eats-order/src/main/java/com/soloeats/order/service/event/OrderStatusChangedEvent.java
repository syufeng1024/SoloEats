package com.soloeats.order.service.event;

/**
 * 订单状态流转完成事件。由 OrderApplicationService.transit 在状态流水落库后发布，
 * 事务提交后才由 app 层监听器转成站内信与 WS 推送；
 * 消息模块不依赖 order，映射与模板选择放在 solo-eats-app。
 */
public record OrderStatusChangedEvent(
        Long orderId,
        Long userId,
        String orderNo,
        Integer fromStatus,
        Integer toStatus
) {
}
