package com.soloeats.product.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.product.service.command.PutOnShopCommand;
import com.soloeats.product.service.command.UpdateShopSpuCommand;
import com.soloeats.product.service.view.ShopSpuView;

public interface ShopSpuService {

    /**
     * 上架到门店；重复上架抛 40104。
     */
    ShopSpuView putOn(Long merchantId, PutOnShopCommand command);

    PageResult<ShopSpuView> page(Long shopId, Integer status, String keyword,
                                 long pageNum, long pageSize);

    ShopSpuView update(Long id, Long merchantId, UpdateShopSpuCommand command);

    /**
     * 0上架 1下架 2估清。2/0 会联动 SKU 级估清状态。
     */
    void updateStatus(Long id, Long merchantId, Integer status);

    PageResult<ShopSpuView> adminPage(Long shopId, Integer auditStatus, String keyword,
                                      long pageNum, long pageSize);

    /**
     * 平台审核：2通过（同时上架） / 3驳回（同时下架）。
     */
    ShopSpuView audit(Long id, Integer auditStatus, String remark);
}
