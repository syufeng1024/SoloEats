package com.soloeats.service.service.command;

import java.util.List;

/**
 * 创建工单。source/submitterType/submitterId 由调用端注入，不接受请求体直传。
 */
public record CreateTicketCommand(
        Integer ticketType,
        Long orderId,
        String subject,
        String content,
        List<String> images,
        Integer priority,
        Integer source,
        Integer submitterType,
        Long submitterId
) {
}
