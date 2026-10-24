package com.soloeats.message.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.message.domain.entity.PushLog;
import com.soloeats.message.domain.enums.ReceiverType;
import com.soloeats.message.mapper.PushLogMapper;
import com.soloeats.message.service.NotifySettingService;
import com.soloeats.message.service.PushService;
import com.soloeats.message.service.command.PushPayload;
import com.soloeats.message.ws.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一 WS 出口。每次调用都落一条 t_push_log：
 * 开关关闭 status=0，在线送达 status=1，离线或发送失败 status=2。
 *
 * <p>TODO(P1 多实例)：当前只推本机会话。多实例部署需改为 Redis Pub/Sub 广播，
 * 各节点订阅后调用 WebSocketSessionRegistry.sendTo，本类的开关判断与日志语义不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

    private static final String CHANNEL_WS = "WS";
    private static final int STATUS_SKIPPED = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int MAX_ERROR_LENGTH = 255;

    private final WebSocketSessionRegistry sessionRegistry;
    private final NotifySettingService notifySettingService;
    private final PushLogMapper pushLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void push(Integer receiverType, Long receiverId, PushPayload payload) {
        if (!ReceiverType.isValid(receiverType) || receiverId == null || payload == null) {
            log.warn("push skipped invalid args receiverType={} receiverId={}", receiverType, receiverId);
            return;
        }

        if (!notifySettingService.isEnabled(receiverType, receiverId, payload.notifyType())) {
            writeLog(payload.messageId(), receiverType, receiverId, STATUS_SKIPPED, "disabled");
            log.info("push skipped by notify setting receiver={}:{} notifyType={} type={}",
                    receiverType, receiverId, payload.notifyType(), payload.type());
            return;
        }

        String body;
        try {
            body = serialize(payload);
        } catch (Exception ex) {
            writeLog(payload.messageId(), receiverType, receiverId, STATUS_FAILED, "serialize_failed");
            log.error("push serialize failed receiver={}:{} type={}",
                    receiverType, receiverId, payload.type(), ex);
            return;
        }

        boolean delivered = sessionRegistry.sendTo(receiverType, receiverId, body);
        if (delivered) {
            writeLog(payload.messageId(), receiverType, receiverId, STATUS_SUCCESS, null);
            log.info("push delivered receiver={}:{} type={} messageId={}",
                    receiverType, receiverId, payload.type(), payload.messageId());
        } else {
            writeLog(payload.messageId(), receiverType, receiverId, STATUS_FAILED, "offline");
            log.info("push offline, site message kept receiver={}:{} type={} messageId={}",
                    receiverType, receiverId, payload.type(), payload.messageId());
        }
    }

    /** 只输出非空字段：订单节点消息带 orderId/orderNo/status，普通站内信只有标题与内容。 */
    private String serialize(PushPayload payload) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", payload.type());
        if (payload.messageId() != null) {
            body.put("messageId", payload.messageId());
        }
        if (payload.orderId() != null) {
            body.put("orderId", payload.orderId());
        }
        if (payload.orderNo() != null) {
            body.put("orderNo", payload.orderNo());
        }
        if (payload.status() != null) {
            body.put("status", payload.status());
        }
        body.put("title", payload.title());
        body.put("content", payload.content());
        return objectMapper.writeValueAsString(body);
    }

    /** 日志写入失败只记录，不向调用方抛出：推送日志不能反向影响订单与站内信。 */
    private void writeLog(Long messageId, Integer receiverType, Long receiverId,
                          int status, String errorMsg) {
        PushLog record = new PushLog();
        record.setMessageId(messageId);
        record.setChannel(CHANNEL_WS);
        record.setReceiverType(receiverType);
        record.setReceiverId(receiverId);
        record.setStatus(status);
        record.setErrorMsg(errorMsg == null ? null : truncate(errorMsg));
        record.setSentAt(status == STATUS_SUCCESS ? LocalDateTime.now() : null);
        try {
            pushLogMapper.insert(record);
        } catch (Exception ex) {
            log.error("push log write failed receiver={}:{} status={}",
                    receiverType, receiverId, status, ex);
        }
    }

    private String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
