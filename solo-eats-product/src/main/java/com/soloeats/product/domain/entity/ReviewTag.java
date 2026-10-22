package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价标签库：tag_type 1好评 2差评。
 */
@Data
@TableName("t_review_tag")
public class ReviewTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String tagName;

    private Integer tagType;

    private Integer sort;

    private Integer status;

    private LocalDateTime createdAt;
}
