package com.soloeats.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_home_page")
public class HomePage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** USER_HOME / USER_MINE */
    private String pageCode;

    private String pageName;

    /** 0草稿 1已发布 2已下线 */
    private Integer status;

    private LocalDateTime publishAt;

    /** 发布版本号，每次发布 +1，用于缓存键 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
