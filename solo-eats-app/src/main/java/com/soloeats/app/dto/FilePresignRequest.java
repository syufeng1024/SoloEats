package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilePresignRequest(
        @NotBlank(message = "fileName 不能为空")
        @Size(max = 200, message = "fileName 长度不能超过 200")
        String fileName,

        @NotBlank(message = "contentType 不能为空")
        @Size(max = 64, message = "contentType 长度不能超过 64")
        String contentType,

        @NotBlank(message = "bizType 不能为空")
        @Size(max = 32, message = "bizType 长度不能超过 32")
        String bizType
) {
}
