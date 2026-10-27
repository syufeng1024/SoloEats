package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服快捷话术（V25 建表）。逻辑删除由 @TableLogic 统一改写：
 * 查询自动追加 deleted = 0，deleteById 变成 UPDATE deleted = 1。
 */
@Data
@TableName("t_quick_reply")
public class QuickReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 分类，最长 64，前端按此分组展示 */
    private String category;

    /** 话术内容，纯文本，最长 500 */
    private String content;

    private Integer sort;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
