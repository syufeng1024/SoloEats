package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮件账号请求。password 更新时留空表示保持原值；新增时由服务层强校验。
 */
public record SaveMailAccountRequest(
        @NotBlank(message = "accountName 不能为空")
        @Size(max = 64, message = "accountName 长度不能超过 64")
        String accountName,

        @NotBlank(message = "host 不能为空")
        @Size(max = 128, message = "host 长度不能超过 128")
        String host,

        @Min(value = 1, message = "port 必须在 1~65535")
        @Max(value = 65535, message = "port 必须在 1~65535")
        Integer port,

        @NotBlank(message = "username 不能为空")
        @Size(max = 128, message = "username 长度不能超过 128")
        String username,

        @Size(max = 256, message = "password 长度不能超过 256")
        String password,

        @NotBlank(message = "fromAddr 不能为空")
        @Size(max = 128, message = "fromAddr 长度不能超过 128")
        String fromAddr,

        @Min(value = 0, message = "ssl 只能是 0 或 1")
        @Max(value = 1, message = "ssl 只能是 0 或 1")
        Integer ssl,

        @Min(value = 0, message = "status 只能是 0 或 1")
        @Max(value = 1, message = "status 只能是 0 或 1")
        Integer status
) {
}
