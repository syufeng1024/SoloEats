package com.soloeats.store.service;

import com.soloeats.store.service.command.CreateShopAccountCommand;
import com.soloeats.store.service.view.ShopAccountView;

import java.util.List;

public interface ShopAccountService {

    ShopAccountView create(Long shopId, Long merchantId, CreateShopAccountCommand command);

    List<ShopAccountView> list(Long shopId, Long merchantId);

    /**
     * 解绑：只逻辑删除 t_shop_account，不删除 t_sys_user。
     */
    void unbind(Long shopId, Long merchantId, Long userId);
}
