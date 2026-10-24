package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端用户标签定义。字段以 V11 建表为准：tag_name / tag_rule / auto_tag。
 * 自动打标（auto_tag = 1）当前只落标记，规则执行留 TODO。
 */
@Data
@TableName("t_member_tag")
public class MemberTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String tagName;

    /** 分群规则描述；自动打标 TODO */
    private String tagRule;

    /** 0 手动 1 自动 */
    private Integer autoTag;

    /** 0 启用 1 停用 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
