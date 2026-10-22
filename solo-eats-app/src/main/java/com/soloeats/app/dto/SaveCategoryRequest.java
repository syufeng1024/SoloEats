package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
        Long parentId,
        @NotBlank(message = "categoryName 不能为空")
        @Size(max = 64, message = "categoryName 长度不能超过 64")
        String categoryName,
        @Size(max = 512, message = "iconUrl 长度不能超过 512")
        String iconUrl,
        Integer sort,
        Integer status
) {
}
