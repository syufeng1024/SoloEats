package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运费加价规则。rule_type：1距离区间 2时段加价 3恶劣天气 4重量 5楼层。
 */
@Data
@TableName("t_freight_rule")
public class FreightRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long templateId;

    private Integer ruleType;

    /** JSON 条件，由 FreightServiceImpl 解析；空条件视为不生效。 */
    private String conditionJson;

    private Long feeDelta;

    private Integer sort;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
