package com.soloeats.product.mapper;

import lombok.Data;

/**
 * 估清原因统计投影。
 */
@Data
public class ReasonStatRow {

    private String reason;

    private Long count;
}
