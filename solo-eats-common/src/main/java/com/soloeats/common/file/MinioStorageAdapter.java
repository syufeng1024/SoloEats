package com.soloeats.common.file;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 适配。异常只记录 objectKey 与操作名，绝不输出 access-key/secret-key 或预签名 URL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.error("minio upload failed objectKey={} reason={}", objectKey, ex.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            // 调用方按「失败只告警」处理，这里保留原始语义供上层记录
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "对象删除失败");
        }
    }

    @Override
    public String presign(String objectKey, String contentType, Duration expiry) {
        try {
            long seconds = Math.max(60L, expiry.toSeconds());
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry((int) seconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            log.error("minio presign failed objectKey={} reason={}", objectKey, ex.getMessage());
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "预签名生成失败");
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.error("minio download failed objectKey={} reason={}", objectKey, ex.getMessage());
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在或对象存储不可用");
        }
    }
}
