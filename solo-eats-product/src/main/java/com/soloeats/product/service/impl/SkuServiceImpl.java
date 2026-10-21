package com.soloeats.product.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;

    @Override
    public Sku getRequired(Long skuId, Long shopId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null
                || !Objects.equals(sku.getShopId(), shopId)
                || sku.getStatus() == null
                || sku.getStatus() != 0
                || sku.getPrice() == null
                || sku.getPrice() <= 0) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku;
    }
}
