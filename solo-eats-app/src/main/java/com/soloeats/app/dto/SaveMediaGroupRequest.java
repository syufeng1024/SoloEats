package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveMediaGroupRequest(
        @NotBlank(message = "groupName 不能为空")
        @Size(max = 64, message = "groupName 长度不能超过 64")
        String groupName,
        Long parentId,
        Integer sort,
        Integer status
) {
}
