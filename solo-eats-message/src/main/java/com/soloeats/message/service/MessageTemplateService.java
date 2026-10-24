package com.soloeats.message.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.message.service.command.SaveMessageTemplateCommand;
import com.soloeats.message.service.query.MessageTemplateQuery;
import com.soloeats.message.service.view.MessageTemplateView;
import com.soloeats.message.service.view.RenderedMessage;

import java.util.Map;

public interface MessageTemplateService {

    PageResult<MessageTemplateView> page(MessageTemplateQuery query, long pageNum, long pageSize);

    MessageTemplateView save(SaveMessageTemplateCommand command);

    MessageTemplateView update(Long id, SaveMessageTemplateCommand command);

    /**
     * 按 code 取模板并替换 {key}。模板不存在或已停用抛 RESOURCE_NOT_FOUND；
     * 未提供值的占位符原样保留并打 warn 日志。
     */
    RenderedMessage render(String templateCode, Map<String, String> params);
}
