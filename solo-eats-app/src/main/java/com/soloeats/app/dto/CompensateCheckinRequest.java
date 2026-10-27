package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 补签请求。日期必须是最近 7 天内的过去日期，服务层二次校验。
 */
public record CompensateCheckinRequest(
        @NotNull(message = "date 不能为空")
        LocalDate date
) {
}
