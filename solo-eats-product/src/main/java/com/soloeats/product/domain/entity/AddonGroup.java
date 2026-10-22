package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加料组。max_select=0 表示不限。
 */
@Data
@TableName("t_addon_group")
public class AddonGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String groupName;

    private Integer minSelect;

    private Integer maxSelect;

    private Integer sort;

    @TableField("is_template")
    private Integer isTemplate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
