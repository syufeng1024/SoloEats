package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SPU 与加料组关联，uk_spu_addon 保证幂等。
 */
@Data
@TableName("t_spu_addon_rel")
public class SpuAddonRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long spuId;

    private Long groupId;
}
