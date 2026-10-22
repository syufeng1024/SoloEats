package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户套餐。平台级表，不带 tenant_id，不参与行级租户过滤。
 * menuIds 以 JSON 数组字符串落库（如 [1,3,4]），由服务层读写。
 */
@Data
@TableName("t_sys_package")
public class SysPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packageCode;

    private String packageName;

    /** JSON 数组字符串；null 表示不限制菜单范围 */
    private String menuIds;

    private Integer maxShop;

    private Integer maxUser;

    /** 套餐价格，单位：分 */
    private Long price;

    private Integer validDays;

    /** 0 启用 1 停用 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
