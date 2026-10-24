package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("t_rider_schedule")
public class RiderSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long riderId;

    private LocalDate workDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 0待确认 1已确认 2请假 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
