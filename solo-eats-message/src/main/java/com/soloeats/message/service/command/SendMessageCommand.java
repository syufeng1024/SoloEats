package com.soloeats.message.service.command;

import java.util.Map;

/**
 * 站内信发送入参。params 供 {key} 占位替换；pushTarget 为空时只发站内信，
 * 非空时同时以 pushTarget.type 下发 WS（订单节点消息用 ORDER_STATUS）。
 */
public record SendMessageCommand(
        Integer receiverType,
        Long receiverId,
        String templateCode,
        Map<String, String> params,
        String bizType,
        Long bizId,
        PushTarget pushTarget
) {

    public record PushTarget(String type, Long orderId, String orderNo, Integer status) {
    }
}
