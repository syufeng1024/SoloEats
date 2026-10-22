package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MailTestRequest(
        @NotNull(message = "accountId 不能为空")
        Long accountId,

        @NotBlank(message = "toAddr 不能为空")
        @Size(max = 128, message = "toAddr 长度不能超过 128")
        String toAddr
) {
}
