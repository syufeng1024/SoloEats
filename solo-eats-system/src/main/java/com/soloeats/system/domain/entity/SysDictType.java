package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sys_dict_type")
public class SysDictType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 字典类型编码，如 order_status */
    private String dictType;

    private String dictName;

    /** 0 启用 1 停用 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
