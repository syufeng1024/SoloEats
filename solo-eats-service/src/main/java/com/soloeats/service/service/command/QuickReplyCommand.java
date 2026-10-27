package com.soloeats.service.service.command;

/**
 * 快捷话术新增/编辑。status：0 启用、1 停用。
 */
public record QuickReplyCommand(
        String category,
        String content,
        Integer sort,
        Integer status
) {
}
