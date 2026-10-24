package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息模板。title_tpl / content_tpl 使用 {key} 占位符，由应用层字符串替换，
 * 不引入任何模板引擎（见 T08「关键实现约束」）。
 */
@Data
@TableName("t_message_template")
public class MessageTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 租户内唯一，uk_tenant_code */
    private String templateCode;

    private String templateName;

    /** 1订单 2营销 3系统 4客服 */
    private Integer templateType;

    private String titleTpl;

    private String contentTpl;

    /** 逗号分隔：SITE,WS,PUSH */
    private String channels;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
