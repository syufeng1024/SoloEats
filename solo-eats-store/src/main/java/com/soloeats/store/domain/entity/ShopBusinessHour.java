package com.soloeats.store.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 门店营业时段。crossDay=1 表示 [start, 24:00) ∪ [00:00, end) 的跨天区间。
 */
@Data
@TableName("t_shop_business_hour")
public class ShopBusinessHour {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    /** 1周一 ~ 7周日。 */
    private Integer dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer crossDay;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
