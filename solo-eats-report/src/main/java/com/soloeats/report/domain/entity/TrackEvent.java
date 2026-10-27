package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 埋点事件。只存业务 ID 与设备 ID，不存手机号、身份证等个人信息。
 */
@Data
@TableName("t_track_event")
public class TrackEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String eventType;

    private Long userId;

    private String deviceId;

    private String bizType;

    private Long bizId;

    private String source;

    private String extraJson;

    private LocalDateTime eventAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
