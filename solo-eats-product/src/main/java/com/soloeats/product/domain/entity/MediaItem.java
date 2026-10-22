package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材项。file_id 引用 T03 的 t_file.id，不复制文件元数据。
 */
@Data
@TableName("t_media_item")
public class MediaItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long groupId;

    private Long fileId;

    private Integer mediaType;

    private Integer sort;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
