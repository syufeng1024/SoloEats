package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SPU 与规格组关联，uk_spu_group 保证幂等。
 */
@Data
@TableName("t_spu_spec_rel")
public class SpuSpecRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long spuId;

    private Long groupId;

    private Integer sort;
}
