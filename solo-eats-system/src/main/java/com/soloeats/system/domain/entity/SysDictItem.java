package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sys_dict_item")
public class SysDictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String dictType;

    private String dictLabel;

    private String dictValue;

    private Integer dictSort;

    private String cssClass;

    /** 1 默认项；同一 dict_type 最多 1 条，由服务层保证 */
    @TableField("is_default")
    private Integer isDefault;

    /** 0 启用 1 停用 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
