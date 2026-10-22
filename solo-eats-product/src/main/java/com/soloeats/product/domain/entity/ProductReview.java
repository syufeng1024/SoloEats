package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价。uk_order_item 兜底同一条订单明细只能评价一次；
 * 评价提交后不可编辑，追评单独字段且只能写一次。
 */
@Data
@TableName("t_product_review")
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long orderId;

    private Long orderItemId;

    private Long userId;

    private Long shopId;

    private Long spuId;

    private Long skuId;

    private Long riderId;

    private Integer overallScore;

    private Integer tasteScore;

    private Integer packageScore;

    private Integer deliveryScore;

    private String content;

    private String imagesJson;

    private String tagsJson;

    @TableField("is_anonymous")
    private Integer isAnonymous;

    private Integer status;

    private Integer topFlag;

    private String merchantReply;

    private LocalDateTime replyAt;

    private String appendContent;

    private LocalDateTime appendAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
