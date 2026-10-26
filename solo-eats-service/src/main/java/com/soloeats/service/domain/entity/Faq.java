package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_faq")
public class Faq {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String category;

    private String question;

    /** 纯文本答案 */
    private String answer;

    private Integer sort;

    private Integer viewCount;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
