package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 发布/撤回公告。status=1 发布，status=2 撤回。
 */
public record NoticePublishRequest(
        @NotNull(message = "status 不能为空")
        Integer status
) {
}
