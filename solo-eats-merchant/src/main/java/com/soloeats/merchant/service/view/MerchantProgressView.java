package com.soloeats.merchant.service.view;

import java.time.LocalDateTime;

/**
 * 申请人查询进度：不返回任何账号或敏感字段。
 */
public record MerchantProgressView(
        Long applyId,
        String merchantNo,
        String merchantName,
        String brandName,
        Integer status,
        String auditRemark,
        LocalDateTime updatedAt
) {
}
