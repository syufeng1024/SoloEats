package com.soloeats.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_banner")
public class Banner {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** HOME_TOP / HOME_POPUP / SPLASH / SHOP_TOP */
    private String slotCode;

    private String title;

    private String imageUrl;

    /** 1商品 2店铺 3活动 4页面 5无跳转 */
    private Integer jumpType;

    private String jumpValue;

    private Integer sort;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    private Long clickCount;

    private Long viewCount;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
