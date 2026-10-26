package com.soloeats.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_home_floor")
public class HomeFloor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long pageId;

    /** 1轮播 2金刚区 3秒杀 4神券 5推荐店铺 6猜你喜欢 7公告条 */
    private Integer floorType;

    /** 纯文本，入库前已过滤 HTML */
    private String title;

    /** JSON 对象，保存前已过白名单过滤 */
    private String configJson;

    private Integer sort;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    /** 0全部 1新客 2会员 3指定用户 */
    private Integer audience;

    private String audienceJson;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
