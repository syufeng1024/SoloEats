package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录。同一用户同一天由 uk_user_date 唯一约束兜底。
 *
 * <p>表内没有签到类型列，补签通过 {@code checkin_date < DATE(created_at)} 识别：
 * 正常签到的 checkin_date 等于创建日，补签的 checkin_date 早于创建日。
 * 因此 created_at 必须由应用显式写入，不能依赖 DB 默认值。
 */
@Data
@TableName("t_checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private LocalDate checkinDate;

    /** 连续签到天数，正常签到=昨日连续天数+1，昨日无记录则为 1 */
    private Integer continuousDays;

    /** 本次签到奖励积分 */
    private Integer pointAward;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
