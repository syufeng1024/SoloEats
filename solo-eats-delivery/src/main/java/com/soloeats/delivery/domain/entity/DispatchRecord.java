package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单记录（t_dispatch_record，既有表，无 tenant_id）。
 * dispatch_type：1自动 2抢单 3手动；status：0推送 1接受 2拒绝 3超时 4取消。
 */
@Data
@TableName("t_dispatch_record")
public class DispatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deliveryOrderId;

    private Long riderId;

    private Integer dispatchType;

    private Integer status;

    private Integer score;

    private LocalDateTime expireAt;

    private LocalDateTime respondedAt;

    private LocalDateTime createdAt;
}
