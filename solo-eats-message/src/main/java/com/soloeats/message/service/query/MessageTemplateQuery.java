package com.soloeats.message.service.query;

/**
 * 模板分页条件。keyword 同时匹配 template_code 与 template_name。
 */
public record MessageTemplateQuery(String keyword, Integer templateType, Integer status) {

    public static MessageTemplateQuery empty() {
        return new MessageTemplateQuery(null, null, null);
    }
}
