package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分账户，一个用户一行。余额只能通过
 * {@code PointAccountMapper#changeBalance} 的条件更新修改，避免先查后改。
 */
@Data
@TableName("t_point_account")
public class PointAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Integer balance;

    private Long totalEarned;

    private Long totalConsumed;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
