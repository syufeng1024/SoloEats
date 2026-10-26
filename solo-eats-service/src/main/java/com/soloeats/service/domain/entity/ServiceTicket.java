package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务工单。状态迁移一律走条件更新，实体只承载读模型与插入字段。
 */
@Data
@TableName("t_service_ticket")
public class ServiceTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String ticketNo;

    /** 1订单问题 2退款 3配送 4投诉 5建议 */
    private Integer ticketType;

    /** 1用户 2商家 3骑手 4平台 */
    private Integer source;

    private Integer submitterType;

    private Long submitterId;

    /** 关联订单ID；本模块不反查 t_order */
    private Long orderId;

    private String subject;

    /** 纯文本，禁止渲染 HTML */
    private String content;

    /** JSON 数组字符串，对应 MySQL json 列 */
    private String imagesJson;

    /** 0普通 1紧急 2特急 */
    private Integer priority;

    /** 0待受理 1处理中 2待回访 3已办结 4已关闭 */
    private Integer status;

    private Long assigneeId;

    private LocalDateTime acceptAt;

    private LocalDateTime deadlineAt;

    private LocalDateTime finishAt;

    private Integer satisfaction;

    private String satisfactionRemark;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
