package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀场次。
 *
 * <p>status 是写入时快照：0待开始 1进行中 2已结束。真实状态由
 * {@code session_start/session_end} 与当前时间推导（见 {@code SeckillServiceImpl#effectiveStatus}），
 * 因为本卡不含定时翻转任务；status=2 同时表示人工结束，推导时优先尊重该值。
 */
@Data
@TableName("t_seckill_activity")
public class SeckillActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String activityName;

    private LocalDateTime sessionStart;

    private LocalDateTime sessionEnd;

    private LocalDateTime preheatAt;

    /** 0待开始 1进行中 2已结束 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
