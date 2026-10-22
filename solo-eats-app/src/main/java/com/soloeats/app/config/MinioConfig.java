package com.soloeats.app.config;

import com.soloeats.common.file.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端。bucket 保持私有，对外只暴露后端代理地址 /api/v1/files/{id}/raw。
 *
 * <p>bucket 初始化失败不阻断应用启动（MinIO 短暂不可用不应拖垮订单/支付链路），
 * 上传时会以 70004 明确失败。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        String endpoint = resolveEndpoint(properties);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        ensureBucket(client, properties.getBucket());
        return client;
    }

    private String resolveEndpoint(MinioProperties properties) {
        String endpoint = properties.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("app.minio.endpoint 未配置");
        }
        String trimmed = endpoint.trim();
        if (trimmed.contains("://")) {
            return trimmed;
        }
        return (properties.isSecure() ? "https://" : "http://") + trimmed;
    }

    private void ensureBucket(MinioClient client, String bucket) {
        if (!StringUtils.hasText(bucket)) {
            log.warn("app.minio.bucket 未配置，文件上传将不可用");
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("minio bucket created bucket={}", bucket);
            }
        } catch (Exception ex) {
            // 不记录凭证；只记录 bucket 与异常类型
            log.warn("minio bucket init failed bucket={} reason={}", bucket, ex.getMessage());
        }
    }
}
