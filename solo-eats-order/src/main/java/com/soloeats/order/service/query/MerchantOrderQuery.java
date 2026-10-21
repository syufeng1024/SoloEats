package com.soloeats.order.service.query;

public record MerchantOrderQuery(
        Long merchantId,
        Long shopId,
        Integer status,
        String keyword,
        long pageNum,
        long pageSize
) {
}
