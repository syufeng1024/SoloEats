package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyFeedbackRequest(
        @NotBlank(message = "reply 不能为空")
        @Size(max = 1000, message = "reply 最长 1000 个字符")
        String reply
) {
}
