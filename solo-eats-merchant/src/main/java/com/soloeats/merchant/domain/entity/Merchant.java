package com.soloeats.merchant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String merchantNo;

    private String merchantName;

    private String brandName;

    /** 平台品类；T05 建 t_category 前只存值，不做外键语义。 */
    private Long categoryId;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private String businessLicenseNo;

    private String legalPerson;

    /** AES-GCM 密文，禁止明文落库与返回。 */
    private String legalIdCardCipher;

    private String province;

    private String city;

    private String district;

    private String address;

    /** 0草稿 1待审核 2已通过 3已驳回 4已停用，见 MerchantStatus。 */
    private Integer status;

    private Long auditBy;

    private LocalDateTime auditAt;

    private String auditRemark;

    private BigDecimal commissionRate;

    private Integer level;

    private LocalDateTime openedAt;

    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
