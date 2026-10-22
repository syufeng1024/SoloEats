package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyReviewRequest(
        @NotBlank(message = "reply 不能为空")
        @Size(max = 500, message = "reply 长度不能超过 500")
        String reply
) {
}
