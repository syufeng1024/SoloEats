package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水。{@code uk_biz(source, biz_id)} 保证同一业务事件只落一条，
 * 重复调用时捕获 DuplicateKeyException 直接返回。
 */
@Data
@TableName("t_point_log")
public class PointLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 1下单 2签到 3评价 4邀请 5兑换 6过期 7手工 */
    private Integer source;

    /**
     * 来源业务主键。过期流水存被过期积分流水的 id，
     * 即 uk_biz(6, 原流水id) 保证同一批次只过期一次。
     */
    private Long bizId;

    private Integer point;

    private Integer beforeBalance;

    private Integer afterBalance;

    private LocalDateTime expireAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
