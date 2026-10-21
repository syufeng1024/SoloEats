package com.soloeats.product.service;

import com.soloeats.product.domain.entity.Sku;

public interface SkuService {

    /**
     * 查询上架 SKU，并校验它属于指定门店。
     */
    Sku getRequired(Long skuId, Long shopId);
}
