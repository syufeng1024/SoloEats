package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_coupon_template")
public class CouponTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer couponType;

    private Long thresholdAmount;

    private Long discountAmount;

    private BigDecimal discountRate;

    private Integer bearer;

    private Integer totalQty;

    private Integer issuedQty;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private Integer validDays;

    private Integer status;

    private LocalDateTime createdAt;
}
