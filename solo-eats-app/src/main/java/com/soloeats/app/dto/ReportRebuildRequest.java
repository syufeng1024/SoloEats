package com.soloeats.app.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 手动重算指定日的报表聚合。日期不得晚于今天。
 */
public record ReportRebuildRequest(
        @NotNull LocalDate date
) {
}
