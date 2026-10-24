package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单策略配置。权重从本表读取，禁止在 Java 里写死。
 */
@Data
@TableName("t_dispatch_config")
public class DispatchConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1全局 2城市 3门店 */
    private Integer configScope;

    private Long shopId;

    private BigDecimal distanceWeight;

    private BigDecimal loadWeight;

    private BigDecimal ratingWeight;

    private BigDecimal timeoutWeight;

    private Integer dispatchExpireSeconds;

    private Integer maxRetry;

    private Integer grabRadiusM;

    private Integer grabMaxOrders;

    private Integer grabEnabled;

    private Integer autoDispatchEnabled;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
