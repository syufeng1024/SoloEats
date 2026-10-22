package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材分组。parent_id=0 为根分组。
 */
@Data
@TableName("t_media_group")
public class MediaGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String groupName;

    private Long parentId;

    private Integer sort;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
