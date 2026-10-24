package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户通知开关。无记录视为开启，uk_receiver_type 保证一个接收者每类通知只有一行。 */
@Data
@TableName("t_notify_setting")
public class NotifySetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1用户 2商家 3骑手 */
    private Integer receiverType;

    private Long receiverId;

    /** 1订单 2营销 3系统 4会话 */
    private Integer notifyType;

    /** 0关闭 1开启 */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
