package com.soloeats.store.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送围栏。圆形用 center + radius，多边形用 polygonJson（[[lng,lat],...]）。
 */
@Data
@TableName("t_shop_delivery_area")
public class ShopDeliveryArea {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    /** 1圆形 2多边形。 */
    private Integer areaType;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    private Integer radiusM;

    private String polygonJson;

    /** 0启用 1停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
