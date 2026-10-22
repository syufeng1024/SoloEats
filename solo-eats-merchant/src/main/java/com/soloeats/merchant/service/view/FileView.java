package com.soloeats.merchant.service.view;

/**
 * 上传结果。url 为后端代理地址，不暴露 MinIO 端口与对象 key 访问凭证。
 */
public record FileView(
        Long fileId,
        String url,
        String fileKey,
        long sizeBytes,
        String contentType,
        String originalName
) {
}
