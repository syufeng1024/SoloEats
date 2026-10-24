package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端收货地址。收货人手机号与账号手机号同样走三列方案：
 * 密文入库、哈希/掩码用于展示，任何接口不得返回明文。
 */
@Data
@TableName("t_member_address")
public class MemberAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String receiverName;

    /** 收货人手机号 AES-GCM 密文 */
    private String receiverPhoneCipher;

    /** 收货人手机号脱敏展示值，如 139****0003 */
    private String receiverPhoneMask;

    private String province;

    private String city;

    private String district;

    private String address;

    /** 门牌号 */
    private String houseNumber;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 家 / 公司 / 学校 */
    private String tag;

    /** 0 否 1 默认 */
    private Integer isDefault;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
