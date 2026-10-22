package com.soloeats.product.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.product.service.command.SaveSpuCommand;
import com.soloeats.product.service.view.SpuDetailView;
import com.soloeats.product.service.view.SpuView;

import java.util.List;

public interface SpuService {

    PageResult<SpuView> adminPage(Long categoryId, Integer status, String keyword,
                                  long pageNum, long pageSize);

    SpuView create(SaveSpuCommand command);

    SpuView update(Long id, SaveSpuCommand command);

    void updateStatus(Long id, Integer status);

    /**
     * 商家可用商品库：平台公共（merchant_id IS NULL）+ 本商家私有，且已上架。
     */
    List<SpuView> merchantLibrary(Long merchantId, Long categoryId, String keyword);

    /**
     * 用户端商品详情，仅已上架商品可见。
     */
    SpuDetailView detail(Long spuId);
}
