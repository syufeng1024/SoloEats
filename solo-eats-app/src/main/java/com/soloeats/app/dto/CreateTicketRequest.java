package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户提交工单。提交者身份取令牌，不接受请求体传入。
 */
public record CreateTicketRequest(
        @NotNull(message = "ticketType 不能为空")
        Integer ticketType,
        Long orderId,
        @NotBlank(message = "subject 不能为空")
        @Size(max = 200, message = "subject 最长 200 个字符")
        String subject,
        @NotBlank(message = "content 不能为空")
        @Size(max = 2000, message = "content 最长 2000 个字符")
        String content,
        @Size(max = 9, message = "图片最多 9 张")
        List<@Size(max = 512, message = "图片地址最长 512 个字符") String> images,
        @NotNull(message = "priority 不能为空")
        @Min(value = 0, message = "priority 只能是 0普通 1紧急 2特急")
        @Max(value = 2, message = "priority 只能是 0普通 1紧急 2特急")
        Integer priority
) {
}
