package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 全量覆盖用户标签：传空数组表示清空该用户全部标签。
 */
public record AssignTagsRequest(
        @NotNull(message = "tagIds 不能为空")
        @Size(max = 50, message = "单个用户最多绑定 50 个标签")
        List<@NotNull(message = "tagId 不能为空") Long> tagIds
) {
}
