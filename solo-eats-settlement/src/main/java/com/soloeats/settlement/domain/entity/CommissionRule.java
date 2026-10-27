package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣规则。命中优先级：活动级(4) > 商家级(3) > 品类级(2) > 全局默认(1)；
 * 同一 scope 内取 {@code priority DESC, effective_from DESC} 第一条。
 *
 * <p>tenant_id 由 TenantLineInnerInterceptor 自动读写，实体不映射该列。
 */
@Data
@TableName("t_commission_rule")
public class CommissionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1全局 2品类 3商家 4活动，见 {@code RuleScope}。 */
    private Integer ruleScope;

    /** 品类ID / 商家ID / 活动ID；rule_scope = 1 时为 NULL。 */
    private Long refId;

    /** 佣金比例，精度 decimal(6,4)，如 0.0600。 */
    private BigDecimal commissionRate;

    /** 订单金额下限（分），NULL 不限。 */
    private Long minAmount;

    /** 订单金额上限（分），NULL 不限。 */
    private Long maxAmount;

    private LocalDateTime effectiveFrom;

    /** NULL 表示长期有效。 */
    private LocalDateTime effectiveTo;

    /** 同 scope 内排序，越大越优先。 */
    private Integer priority;

    /** 0停用 1启用。 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 0正常 1删除。 */
    private Integer deleted;
}
