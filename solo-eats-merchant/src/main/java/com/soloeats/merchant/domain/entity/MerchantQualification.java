package com.soloeats.merchant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_merchant_qualification")
public class MerchantQualification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long merchantId;

    /** 1营业执照 2食品经营许可证 3法人身份证正面 4法人身份证反面 5门头照 6店内照 7其他。 */
    private Integer qualType;

    private Long fileId;

    private String fileUrl;

    private String fileName;

    private LocalDate expireAt;

    /** 0待审 1通过 2驳回。 */
    private Integer status;

    private String auditRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
