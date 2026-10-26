package com.soloeats.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SaveAppVersionRequest(
        @NotNull(message = "appType 不能为空")
        @Min(value = 1, message = "appType 只能是 1用户端 / 2骑手端 / 3商家端")
        @Max(value = 3, message = "appType 只能是 1用户端 / 2骑手端 / 3商家端")
        Integer appType,

        @NotBlank(message = "versionName 不能为空")
        @Size(max = 32, message = "versionName 长度不能超过 32")
        String versionName,

        @NotNull(message = "versionCode 不能为空")
        @Min(value = 1, message = "versionCode 必须是正整数")
        Integer versionCode,

        @Size(max = 1000, message = "updateDesc 长度不能超过 1000")
        String updateDesc,

        @NotBlank(message = "downloadUrl 不能为空")
        @Size(max = 512, message = "downloadUrl 长度不能超过 512")
        String downloadUrl,

        @Min(value = 0, message = "forceUpdate 只能是 0 或 1")
        @Max(value = 1, message = "forceUpdate 只能是 0 或 1")
        Integer forceUpdate,

        @Min(value = 0, message = "grayPercent 必须在 0~100 之间")
        @Max(value = 100, message = "grayPercent 必须在 0~100 之间")
        Integer grayPercent,

        @Min(value = 0, message = "status 只能是 0停用 / 1启用")
        @Max(value = 1, message = "status 只能是 0停用 / 1启用")
        Integer status,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime publishAt
) {
}
