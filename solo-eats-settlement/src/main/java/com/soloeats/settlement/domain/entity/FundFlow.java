package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台资金流水。uk_biz(flow_type, biz_type, biz_id) 保证同一业务单据
 * 同一流水类型只记一次；平台无统一账户余额，balance_after 一律留 NULL。
 */
@Data
@TableName("t_fund_flow")
public class FundFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1佣金收入 2结算支出 3补贴支出 4优惠成本 5提现支出 6退款冲抵，见 {@code FlowType}。 */
    private Integer flowType;

    /** 业务类型，如 commission_record / payout_order。 */
    private String bizType;

    private Long bizId;

    /** 1入 2出。 */
    private Integer direction;

    /** 流水金额，分。 */
    private Long amount;

    /** 余额快照，平台无统一账户，恒为 NULL。 */
    private Long balanceAfter;

    private String remark;

    private LocalDateTime createdAt;
}
