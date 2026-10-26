package com.soloeats.service.service.command;

import java.util.List;

/**
 * 用户提交投诉。complainantId 取登录态，不接受请求体传入。
 */
public record CreateComplaintCommand(
        Long orderId,
        Integer targetType,
        Long targetId,
        String reason,
        List<String> images,
        Long complainantId
) {
}
