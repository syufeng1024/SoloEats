package com.soloeats.merchant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_file 元数据。对象存储不可变，换图必须新建记录，禁止改写 file_key。
 */
@Data
@TableName("t_file")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String fileKey;

    private String bucket;

    private String originalName;

    private String contentType;

    private Long sizeBytes;

    /** MERCHANT_QUAL/PRODUCT/AVATAR/TICKET/CONTENT/RECEIPT。 */
    private String bizType;

    /** 业务ID；申请阶段绑定 applyId。 */
    private Long bizId;

    private Long uploaderId;

    /** USER/ADMIN/APPLY。 */
    private String uploaderType;

    private String url;

    /** 0正常 1已删除。 */
    private Integer status;

    private LocalDateTime createdAt;
}
