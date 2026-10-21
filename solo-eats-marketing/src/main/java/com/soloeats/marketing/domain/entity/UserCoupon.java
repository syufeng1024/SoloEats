package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long templateId;

    private Integer status;

    private Long orderId;

    private LocalDateTime lockedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expireAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
