package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_rider_online_log")
public class RiderOnlineLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long riderId;

    /** 1上线 2下线 */
    private Integer action;

    private BigDecimal lng;

    private BigDecimal lat;

    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
