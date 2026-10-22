package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加料项。price 必须大于等于 0；stock 为空表示不限量。
 */
@Data
@TableName("t_addon_item")
public class AddonItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long groupId;

    private String itemName;

    private Long price;

    private Integer stock;

    private Integer sort;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
