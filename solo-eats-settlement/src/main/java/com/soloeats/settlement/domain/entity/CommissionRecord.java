package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣计算明细：一单一条（uk_order 幂等）。
 *
 * <p>金额单位一律分。base_amount = 商品金额 + 打包费 − 商家承担优惠（含打包费，
 * 与既有结算单口径一致）；commission_amount = HALF_UP(base_amount * commission_rate)。
 * rule_id / rule_scope 为 NULL 表示未命中任何规则、使用内置默认费率。
 */
@Data
@TableName("t_commission_record")
public class CommissionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long merchantId;

    private Long shopId;

    /** 订单首件商品所属品类，用于品类级规则命中与按品类导出。 */
    private Long categoryId;

    /** 抽佣基数，分。 */
    private Long baseAmount;

    /** 命中规则ID；内置默认费率时为 NULL。 */
    private Long ruleId;

    /** 命中规则 scope；内置默认费率时为 NULL。 */
    private Integer ruleScope;

    /** 实际使用的佣金比例。 */
    private BigDecimal commissionRate;

    /** 抽佣金额，分。 */
    private Long commissionAmount;

    /** 归集到的结算单ID，账单生成后回填。 */
    private Long settlementBillId;

    private LocalDateTime createdAt;
}
