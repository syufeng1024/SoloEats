package com.soloeats.member.service.command;

/**
 * 隐私协议确认。agree 为 null 时按同意处理；false 表示撤回同意，同样落一条历史记录。
 */
public record AgreementConfirmCommand(
        String agreementVersion,
        Boolean agree
) {
}
