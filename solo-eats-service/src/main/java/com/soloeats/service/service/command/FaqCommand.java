package com.soloeats.service.service.command;

/**
 * FAQ 新增/编辑。status：0 启用、1 停用。
 */
public record FaqCommand(
        String category,
        String question,
        String answer,
        Integer sort,
        Integer status
) {
}
