package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规格组：杯型/温度/糖度。is_template=1 表示可复用的模板组。
 */
@Data
@TableName("t_spec_group")
public class SpecGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String groupName;

    private Integer groupType;

    @TableField("is_required")
    private Integer isRequired;

    private Integer sort;

    @TableField("is_template")
    private Integer isTemplate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
