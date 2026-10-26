package com.soloeats.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 楼层批量排序，按数组顺序写入 sort=1..n。 */
public record SortHomeFloorsRequest(
        @NotEmpty(message = "ids 不能为空")
        @Size(max = 100, message = "单次最多排序 100 个楼层")
        List<@NotNull(message = "楼层 ID 不能为空") Long> ids
) {
}
