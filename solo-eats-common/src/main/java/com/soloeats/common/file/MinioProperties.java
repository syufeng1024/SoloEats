package com.soloeats.common.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对应 application.yml 的 {@code app.minio.*}。access-key / secret-key 只用于建立客户端，
 * 禁止写入日志或接口响应。
 */
@Data
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    private boolean secure;
}
