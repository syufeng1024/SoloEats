package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sku")
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long spuId;

    private String skuName;

    private String specJson;

    private String imageUrl;

    private Long price;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
