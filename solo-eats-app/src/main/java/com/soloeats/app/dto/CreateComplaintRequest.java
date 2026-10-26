package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateComplaintRequest(
        @NotNull(message = "orderId 不能为空")
        Long orderId,
        @NotNull(message = "targetType 不能为空")
        Integer targetType,
        @NotNull(message = "targetId 不能为空")
        Long targetId,
        @NotBlank(message = "reason 不能为空")
        @Size(max = 500, message = "reason 最长 500 个字符")
        String reason,
        @Size(max = 9, message = "图片最多 9 张")
        List<@Size(max = 512, message = "图片地址最长 512 个字符") String> images
) {
}
