package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;

/**
 * 秒杀预占请求体。qty 缺省为 1。
 */
public record SeckillReserveRequest(
        @Min(value = 1, message = "qty 必须大于等于 1")
        Integer qty
) {
}
