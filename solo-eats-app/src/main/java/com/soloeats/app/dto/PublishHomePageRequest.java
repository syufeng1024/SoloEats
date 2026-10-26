package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishHomePageRequest(
        @NotBlank(message = "pageCode 不能为空")
        @Size(max = 32, message = "pageCode 长度不能超过 32")
        String pageCode
) {
}
