package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 骑手认证记录。status：0待审 1通过 2驳回（与 t_rider.cert_status 的
 * 0未提交 / 1待审核 / 2已通过 / 3已驳回 不是同一套取值）。
 */
@Data
@TableName("t_rider_certification")
public class RiderCertification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long riderId;

    private String realName;

    private String idCardCipher;

    private Long idCardFrontFileId;

    private Long idCardBackFileId;

    private Long healthCertFileId;

    private LocalDate healthCertExpireAt;

    private Integer status;

    private Long auditBy;

    private LocalDateTime auditAt;

    private String auditRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
