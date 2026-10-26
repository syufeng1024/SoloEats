package com.soloeats.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_search_keyword")
public class SearchKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String keyword;

    /** 1默认词 2热搜词 3联想词 4屏蔽词 */
    private Integer keywordType;

    private Integer weight;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
