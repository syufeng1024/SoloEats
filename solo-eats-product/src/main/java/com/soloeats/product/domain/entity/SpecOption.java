package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规格选项。price_delta 允许为负（如小杯 -200）。
 */
@Data
@TableName("t_spec_option")
public class SpecOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long groupId;

    private String optionName;

    private Long priceDelta;

    private Integer sort;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
