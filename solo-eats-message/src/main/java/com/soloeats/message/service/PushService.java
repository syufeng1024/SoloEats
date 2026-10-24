package com.soloeats.message.service;

import com.soloeats.message.service.command.PushPayload;

/**
 * 统一下发出口。实现必须先查通知开关，再决定 WS 下发并落 t_push_log。
 */
public interface PushService {

    void push(Integer receiverType, Long receiverId, PushPayload payload);
}
