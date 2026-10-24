package com.soloeats.app.message;

import com.soloeats.message.domain.enums.ReceiverType;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.SiteMessageService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.message.service.command.SendMessageCommand;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.service.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 订单状态 → 站内信 + WS 推送。必须放在 app 层：
 * solo-eats-message 不依赖 order，模块边界由本类承担。
 *
 * <p>只映射三个有模板的节点；其余状态（备餐完成、配送中、完成、取消等）
 * 仅推 WS，不写站内信，避免消息中心被低价值节点刷屏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusMessageListener {

    private static final String BIZ_TYPE_ORDER = "ORDER";
    private static final String WS_TYPE_ORDER_STATUS = "ORDER_STATUS";
    /** t_notify_setting.notify_type：1订单。 */
    private static final int NOTIFY_TYPE_ORDER = 1;

    /** toStatus → 模板编码。 */
    private static final Map<Integer, String> STATUS_TEMPLATES = Map.of(
            OrderStatus.WAIT_ACCEPT.getCode(), "ORDER_PAID",
            OrderStatus.PREPARING.getCode(), "ORDER_ACCEPTED",
            OrderStatus.DELIVERED.getCode(), "ORDER_DELIVERED");

    private final SiteMessageService siteMessageService;
    private final PushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event == null || event.userId() == null || event.orderId() == null) {
            return;
        }
        String orderNo = event.orderNo() == null ? "" : event.orderNo();
        // Map.of 不接受 null key 查询，toStatus 缺失时按「仅 WS」处理
        String templateCode = event.toStatus() == null ? null : STATUS_TEMPLATES.get(event.toStatus());

        try {
            if (templateCode != null) {
                siteMessageService.send(new SendMessageCommand(
                        ReceiverType.USER.getCode(),
                        event.userId(),
                        templateCode,
                        Map.of("orderNo", orderNo),
                        BIZ_TYPE_ORDER,
                        event.orderId(),
                        new SendMessageCommand.PushTarget(
                                WS_TYPE_ORDER_STATUS, event.orderId(), orderNo, event.toStatus())));
            } else {
                pushService.push(ReceiverType.USER.getCode(), event.userId(), new PushPayload(
                        NOTIFY_TYPE_ORDER,
                        WS_TYPE_ORDER_STATUS,
                        null,
                        event.orderId(),
                        orderNo,
                        event.toStatus(),
                        "订单状态更新",
                        statusText(event.toStatus())));
            }
        } catch (Exception ex) {
            // 消息失败不能影响已提交的订单状态
            log.error("order status message failed orderId={} toStatus={}",
                    event.orderId(), event.toStatus(), ex);
        }
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "订单状态已更新";
        }
        try {
            return "订单状态已更新为" + OrderStatus.fromCode(status).getDescription();
        } catch (Exception ex) {
            return "订单状态已更新";
        }
    }
}
