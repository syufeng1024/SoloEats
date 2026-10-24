package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDispatchRequest(
        @NotBlank(message = "拒单原因不能为空")
        @Size(max = 128, message = "拒单原因不能超过 128 个字符")
        String reason
) {
}
