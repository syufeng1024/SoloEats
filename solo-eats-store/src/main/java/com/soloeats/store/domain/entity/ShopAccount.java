package com.soloeats.store.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店账号关联。解绑只删本表行，不删除 t_sys_user（同一账号可关联多门店）。
 */
@Data
@TableName("t_shop_account")
public class ShopAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    private Long merchantId;

    private Long userId;

    /** 1店长 2店员。 */
    private Integer accountRole;

    /** 1登录默认门店。 */
    private Integer isDefault;

    /** 0启用 1停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
