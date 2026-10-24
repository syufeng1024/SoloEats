package com.soloeats.message.service.command;

/**
 * 模板新增/修改入参。更新时字段为 null 表示保持原值。
 */
public record SaveMessageTemplateCommand(
        String templateCode,
        String templateName,
        Integer templateType,
        String titleTpl,
        String contentTpl,
        String channels,
        Integer status
) {
}
