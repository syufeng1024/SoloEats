package com.soloeats.app.dto;

import jakarta.validation.constraints.Size;

/**
 * 消息模板新增/修改。更新时未传的字段保持原值，必填校验在 Service 内按合并后的结果做。
 */
public record SaveMessageTemplateRequest(
        @Size(max = 64, message = "模板编码最长 64 个字符") String templateCode,
        @Size(max = 100, message = "模板名称最长 100 个字符") String templateName,
        Integer templateType,
        @Size(max = 200, message = "标题模板最长 200 个字符") String titleTpl,
        @Size(max = 1000, message = "内容模板最长 1000 个字符") String contentTpl,
        @Size(max = 64, message = "通道配置最长 64 个字符") String channels,
        Integer status
) {
}
