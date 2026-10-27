package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员权益发放记录，按（用户、权益类型、周期）唯一。
 * 本批只建表并预留 Mapper，实际发放逻辑在后续批次实现。
 */
@Data
@TableName("t_member_benefit_grant")
public class MemberBenefitGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long levelId;

    /** 1神券 2免配送费 3专属客服 4生日礼遇 */
    private Integer benefitType;

    /** 发放周期，如 2026-10 */
    private String period;

    private Integer grantQty;

    private Integer usedQty;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
