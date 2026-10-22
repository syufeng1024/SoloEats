package com.soloeats.merchant.support;

import com.soloeats.common.file.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步删除对象。t_file 已逻辑删除后调用；对象存储失败只告警，不回滚业务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileObjectRemover {

    private final FileStoragePort fileStoragePort;

    @Async
    public void removeQuietly(String objectKey) {
        try {
            fileStoragePort.delete(objectKey);
        } catch (Exception ex) {
            log.warn("minio object delete failed objectKey={} reason={}", objectKey, ex.getMessage());
        }
    }
}
