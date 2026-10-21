package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sku_stock")
public class SkuStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long skuId;

    private Integer availableQty;

    private Integer lockedQty;

    private Integer soldQty;

    private Boolean soldOut;

    private Integer version;

    private LocalDateTime updatedAt;
}
