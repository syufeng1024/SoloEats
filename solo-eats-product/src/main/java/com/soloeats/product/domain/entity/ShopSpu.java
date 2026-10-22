package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店商品。status 只表示 SPU 在门店的上下架：0上架 1下架 2估清；
 * 与 t_sku.status、t_sku_stock.sold_out 语义分离。
 */
@Data
@TableName("t_shop_spu")
public class ShopSpu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    private Long spuId;

    private Long priceOverride;

    private Integer status;

    private Integer auditStatus;

    private Integer sortWeight;

    private Integer monthSales;

    private LocalDateTime suspendedUntil;

    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
