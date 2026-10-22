package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品库 SPU。merchant_id 为空表示平台公共商品库。
 */
@Data
@TableName("t_spu")
public class Spu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long merchantId;

    private Long categoryId;

    private String spuName;

    private String subtitle;

    private String description;

    private String detailHtml;

    private String mainImage;

    private String imagesJson;

    private String tagsJson;

    private Long packagingFee;

    private Integer minPurchaseQty;

    private Integer limitPerOrder;

    private Integer status;

    private Integer sortWeight;

    private Integer monthSales;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
