package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端收藏。取消收藏为物理删除，不设 deleted；
 * uk_user_target 保证同一用户重复收藏同一对象只会落一条。
 */
@Data
@TableName("t_favorite")
public class Favorite {

    /** 1 店铺 */
    public static final int TARGET_TYPE_SHOP = 1;

    /** 2 商品 */
    public static final int TARGET_TYPE_PRODUCT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 1店铺 2商品 */
    private Integer targetType;

    /** 收藏对象 ID：targetType=1 为 t_shop.id，=2 为 t_spu.id */
    private Long targetId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
