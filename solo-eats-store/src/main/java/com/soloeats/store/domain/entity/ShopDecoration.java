package com.soloeats.store.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店装修配置。configJson 为 JSON 对象字符串，读写在服务层校验。
 */
@Data
@TableName("t_shop_decoration")
public class ShopDecoration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    private String configJson;

    @Version
    private Integer version;

    /** 0启用 1停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
