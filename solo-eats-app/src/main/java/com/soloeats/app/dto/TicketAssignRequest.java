package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TicketAssignRequest(
        @NotNull(message = "assigneeId 不能为空")
        @Min(value = 1, message = "assigneeId 必须为正整数")
        Long assigneeId
) {
}
