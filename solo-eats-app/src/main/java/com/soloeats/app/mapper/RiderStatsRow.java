package com.soloeats.app.mapper;

import lombok.Data;

import java.math.BigDecimal;

/** t_rider 中骑手端展示所需的只读字段。 */
@Data
public class RiderStatsRow {

    private BigDecimal rating;

    /** 百分比数值 0~100。 */
    private BigDecimal onTimeRate;

    private Integer totalOrders;

    private Integer certStatus;
}
