package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String content;

    /** JSON 数组字符串，对应 MySQL json 列 */
    private String imagesJson;

    private String contact;

    /** 0待处理 1已回复 */
    private Integer status;

    private String reply;

    private LocalDateTime replyAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
