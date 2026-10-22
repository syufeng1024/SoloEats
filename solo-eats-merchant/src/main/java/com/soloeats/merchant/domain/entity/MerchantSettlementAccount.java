package com.soloeats.merchant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_merchant_settlement_account")
public class MerchantSettlementAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long merchantId;

    private String accountName;

    private String bankName;

    private String bankBranch;

    /** AES-GCM 密文，禁止明文落库与返回。 */
    private String bankAccountNoCipher;

    private String alipayAccount;

    private Long contractFileId;

    private String contractFileUrl;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /** 1 默认账户；每个商家由应用层保证唯一。 */
    private Integer isDefault;

    /** 0待审 1生效 2停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
