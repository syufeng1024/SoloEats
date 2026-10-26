package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketHandleRequest(
        @NotBlank(message = "content 不能为空")
        @Size(max = 1000, message = "content 最长 1000 个字符")
        String content
) {
}
