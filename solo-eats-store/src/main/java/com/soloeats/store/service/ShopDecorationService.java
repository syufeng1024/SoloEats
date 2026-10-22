package com.soloeats.store.service;

public interface ShopDecorationService {

    /**
     * 未配置时返回 "{}"。
     */
    String get(Long shopId, Long merchantId);

    /**
     * 保存 JSON 对象字符串，非对象或超过 20000 字符拒绝。
     */
    String save(Long shopId, Long merchantId, String configJson);
}
