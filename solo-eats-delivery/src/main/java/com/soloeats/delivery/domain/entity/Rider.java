package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 骑手档案。身份证/手机号只存密文与掩码，任何查询接口不得返回明文。
 */
@Data
@TableName("t_rider")
public class Rider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String riderNo;

    private String realName;

    /** AES-GCM 密文，仅内部解密使用 */
    private String idCardCipher;

    /** 展示用脱敏身份证 */
    private String idCardMask;

    private String phoneCipher;

    private String phoneMask;

    private String avatarUrl;

    /** 1电动车 2摩托 3自行车 4步行，见 VehicleType。 */
    private Integer vehicleType;

    /** 0未提交 1待审核 2已通过 3已驳回，见 RiderCertStatus。 */
    private Integer certStatus;

    private String certRemark;

    private LocalDate healthCertExpireAt;

    private Integer maxConcurrentOrders;

    private Integer pickupRadiusM;

    /** 0正常 1停用 2拉黑，见 RiderStatus。 */
    private Integer status;

    private BigDecimal rating;

    private BigDecimal onTimeRate;

    private Integer totalOrders;

    /** 0离线 1在线 2接单中；实时状态以 Redis 为准，本字段只做兜底展示。 */
    private Integer onlineStatus;

    private LocalDateTime lastOnlineAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
