package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店与运费模板的一对一绑定，uk_shop(shop_id)。
 */
@Data
@TableName("t_shop_freight_rel")
public class ShopFreightRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    private Long templateId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
