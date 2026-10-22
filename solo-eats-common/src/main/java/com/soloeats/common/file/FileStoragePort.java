package com.soloeats.common.file;

import java.io.InputStream;
import java.time.Duration;

/**
 * 对象存储端口。业务模块只依赖本接口，测试可替换为内存实现。
 */
public interface FileStoragePort {

    /**
     * 上传对象。调用方负责生成不可变的 objectKey；同名对象不得覆盖。
     */
    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 删除对象。对象不存在时按成功处理。
     */
    void delete(String objectKey);

    /**
     * 生成预签名 PUT 地址，前端直传对象存储，后端不中转文件内容。
     */
    String presign(String objectKey, String contentType, Duration expiry);

    /**
     * 读取对象。调用方必须关闭返回的流。
     */
    InputStream download(String objectKey);
}
