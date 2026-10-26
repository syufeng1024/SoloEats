package com.soloeats.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_app_version")
public class AppVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1用户端 2骑手端 3商家端 */
    private Integer appType;

    private String versionName;

    private Integer versionCode;

    /** 纯文本，入库前已过滤 HTML */
    private String updateDesc;

    private String downloadUrl;

    /** 1强制更新 */
    private Integer forceUpdate;

    /** 灰度比例 0~100 */
    private Integer grayPercent;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime publishAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
