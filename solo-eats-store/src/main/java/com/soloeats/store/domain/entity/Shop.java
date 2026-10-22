package com.soloeats.store.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店主数据。金额字段一律分（bigint），经纬度 decimal(10,6)。
 */
@Data
@TableName("t_shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long merchantId;

    private String shopNo;

    private String shopName;

    private Long brandId;

    private Long categoryId;

    private String province;

    private String city;

    private String district;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;

    private String contactName;

    private String contactPhone;

    private String logoUrl;

    private String coverUrl;

    /** JSON 数组字符串，如 ["/api/v1/files/1/raw"]。 */
    private String imageUrls;

    private String announcement;

    /** 0休息 1营业 2打烊 3停用，见 ShopStatus。 */
    private Integer status;

    /** 位掩码：1平台配送 2到店自取，见 DeliveryType。 */
    private Integer deliveryTypes;

    private Long minOrderAmount;

    private Long deliveryFee;

    private Long packagingFee;

    private Integer prepMinutes;

    private Integer deliveryRadiusM;

    private BigDecimal rating;

    private BigDecimal ratingTaste;

    private BigDecimal ratingPackage;

    private BigDecimal ratingDelivery;

    private Integer monthSales;

    private Integer sortWeight;

    private Integer pickupEnabled;

    private Integer pickupPrepareMinutes;

    /** 自取订单超时未取时长（分钟）。 */
    private Integer pickupTimeoutMinutes;

    private String pickupCodePrefix;

    private Integer pickupCodeLength;

    /** 取餐码有效期（分钟），与 pickupTimeoutMinutes 语义不同。 */
    private Integer pickupCodeValidMinutes;

    /** 自取立减金额（分）。 */
    private Long pickupDiscount;

    private Long settleAccountId;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
