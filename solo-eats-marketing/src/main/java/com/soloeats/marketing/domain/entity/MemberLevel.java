package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员等级配置。成长值门槛与权益均后台可改，禁止在 Java 里写死档位。
 */
@Data
@TableName("t_member_level")
public class MemberLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 等级编码，如 V1；租户内唯一 */
    private String levelCode;

    private String levelName;

    /** 等级序号 1~6 */
    private Integer levelNo;

    /** 进入该等级所需的最小近 365 天成长值 */
    private Integer growthThreshold;

    /** 展示星级 1~3 */
    private Integer star;

    /** 权益 JSON，如 {"couponCount":4,"freeDeliveryTimes":3,"exclusiveService":true} */
    private String benefitsJson;

    private String iconUrl;

    private Integer sort;

    /** 0启用 1停用 */
    private Integer status;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
