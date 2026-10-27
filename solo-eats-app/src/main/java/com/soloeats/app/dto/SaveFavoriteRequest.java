package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 收藏请求。userId 只取 JWT，不接受请求体传入。
 */
public record SaveFavoriteRequest(
        @NotNull(message = "targetType 不能为空")
        @Min(value = 1, message = "targetType 只能是 1店铺 2商品")
        @Max(value = 2, message = "targetType 只能是 1店铺 2商品")
        Integer targetType,
        @NotNull(message = "targetId 不能为空")
        @Positive(message = "targetId 必须是正整数")
        Long targetId
) {
}
