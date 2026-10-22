package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

public record SaveMediaItemRequest(
        @NotNull(message = "fileId 不能为空")
        Long fileId,
        Integer mediaType,
        Integer sort,
        Integer status
) {
}
