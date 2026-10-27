package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀商品与独立库存（不占用 t_sku_stock）。
 *
 * <p>库存扣减走 {@code SeckillItemMapper#increaseSoldQty} 的条件更新，不依赖乐观锁；
 * {@link Version} 字段只为与表结构保持一致，项目已注册 OptimisticLockerInnerInterceptor，
 * 后续若改用 updateById 会自动带上版本条件。
 */
@Data
@TableName("t_seckill_item")
public class SeckillItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long activityId;

    private Long shopId;

    private Long spuId;

    private Long skuId;

    /** 秒杀价，分 */
    private Long seckillPrice;

    /** 秒杀总库存 */
    private Integer seckillStock;

    /** 已售/已预占数量 */
    private Integer soldQty;

    /** 每人限购 */
    private Integer limitPerUser;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
