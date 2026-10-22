package com.soloeats.product.service;

import com.soloeats.product.service.view.ShopMenuView;

import java.util.List;

public interface ShopMenuService {

    /**
     * 用户端菜单聚合：固定两条批量 SQL，结果缓存 Redis 版本化 key。
     */
    List<ShopMenuView> menu(Long shopId);

    /**
     * 商品/门店商品变更后失效缓存：递增版本号并删除旧版本 key。
     */
    void evictMenuCache(Long shopId);

    /**
     * 平台品类/SPU 变更会影响多个门店的菜单，按 key 前缀清理。
     */
    void evictAllMenuCache();
}
