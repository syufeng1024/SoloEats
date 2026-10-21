package com.soloeats.product.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.product.service.command.CreateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuStockCommand;
import com.soloeats.product.service.view.MerchantSkuView;

public interface MerchantSkuService {

    PageResult<MerchantSkuView> page(Long shopId, Integer status, String keyword,
                                     long pageNum, long pageSize);

    MerchantSkuView create(Long shopId, CreateSkuCommand command);

    MerchantSkuView update(Long shopId, Long skuId, UpdateSkuCommand command);

    MerchantSkuView updateStock(Long shopId, Long skuId, UpdateSkuStockCommand command);
}
