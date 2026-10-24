package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运费模板。金额单位分，全部 long。
 */
@Data
@TableName("t_freight_template")
public class FreightTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String templateName;

    private Long baseFee;

    private Integer baseDistanceM;

    private Integer perStepDistanceM;

    private Long perStepFee;

    private Integer maxDistanceM;

    /** 满额免配送费门槛；null 表示不减免。 */
    private Long freeOverAmount;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
