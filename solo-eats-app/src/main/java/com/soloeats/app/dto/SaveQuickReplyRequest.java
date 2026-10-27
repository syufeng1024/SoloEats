package com.soloeats.app.dto;

import com.soloeats.service.service.command.QuickReplyCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveQuickReplyRequest(
        @NotBlank(message = "category 不能为空")
        @Size(max = 64, message = "category 最长 64 个字符")
        String category,
        @NotBlank(message = "content 不能为空")
        @Size(max = 500, message = "content 最长 500 个字符")
        String content,
        Integer sort,
        @Min(value = 0, message = "status 只能是 0启用 / 1停用")
        @Max(value = 1, message = "status 只能是 0启用 / 1停用")
        Integer status
) {

    public QuickReplyCommand toCommand() {
        return new QuickReplyCommand(category, content, sort, status);
    }
}
