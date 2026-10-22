package com.soloeats.merchant.service.view;

/**
 * 预签名直传结果。uploadUrl 只对指定 objectKey 与有效期生效。
 */
public record PresignView(
        String uploadUrl,
        String fileKey,
        int expiresIn
) {
}
