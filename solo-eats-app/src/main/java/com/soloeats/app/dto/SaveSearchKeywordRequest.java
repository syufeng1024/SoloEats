package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveSearchKeywordRequest(
        @NotBlank(message = "keyword 不能为空")
        @Size(max = 64, message = "keyword 长度不能超过 64")
        String keyword,

        @NotNull(message = "keywordType 不能为空")
        @Min(value = 1, message = "keywordType 只能是 1默认词 / 2热搜词 / 3联想词 / 4屏蔽词")
        @Max(value = 4, message = "keywordType 只能是 1默认词 / 2热搜词 / 3联想词 / 4屏蔽词")
        Integer keywordType,

        Integer weight,

        @Min(value = 0, message = "status 只能是 0停用 / 1启用")
        @Max(value = 1, message = "status 只能是 0停用 / 1启用")
        Integer status
) {
}
