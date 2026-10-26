package com.soloeats.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SaveBannerRequest(
        @NotBlank(message = "slotCode 不能为空")
        @Size(max = 32, message = "slotCode 长度不能超过 32")
        String slotCode,

        @NotBlank(message = "title 不能为空")
        @Size(max = 128, message = "title 长度不能超过 128")
        String title,

        @NotBlank(message = "imageUrl 不能为空")
        @Size(max = 512, message = "imageUrl 长度不能超过 512")
        String imageUrl,

        @NotNull(message = "jumpType 不能为空")
        @Min(value = 1, message = "jumpType 只能是 1~5")
        @Max(value = 5, message = "jumpType 只能是 1~5")
        Integer jumpType,

        @Size(max = 255, message = "jumpValue 长度不能超过 255")
        String jumpValue,

        Integer sort,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime effectiveFrom,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime effectiveTo,

        @Min(value = 0, message = "status 只能是 0停用 / 1启用")
        @Max(value = 1, message = "status 只能是 0停用 / 1启用")
        Integer status
) {
}
