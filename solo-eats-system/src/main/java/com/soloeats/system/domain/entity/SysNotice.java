package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告。P0 只维护 read_count，用户级已读明细留待 P1。
 */
@Data
@TableName("t_sys_notice")
public class SysNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String title;

    private String content;

    /** 1 公告 2 通知 */
    private Integer noticeType;

    /** 1 全部 2 平台 3 商家 4 骑手 */
    private Integer targetType;

    /** 0 草稿 1 已发布 2 已撤回 */
    private Integer status;

    private LocalDateTime publishAt;

    private LocalDateTime expireAt;

    private Integer readCount;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
