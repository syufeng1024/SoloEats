package com.soloeats.merchant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核流水。审计表只追加，不做逻辑删除，也没有 updated_at。
 */
@Data
@TableName("t_merchant_audit_log")
public class MerchantAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long merchantId;

    private Integer fromStatus;

    private Integer toStatus;

    /** SUBMIT/APPROVE/REJECT/ENABLE/DISABLE。 */
    private String action;

    private Long operatorId;

    private String operatorName;

    private String remark;

    private LocalDateTime createdAt;
}
