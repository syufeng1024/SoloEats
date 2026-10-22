package com.soloeats.store.service.view;

import java.time.LocalDateTime;

/**
 * 门店账号。username/nickname 可能为空（账号超过批量查询上限时）。
 */
public record ShopAccountView(
        Long id,
        Long shopId,
        Long userId,
        String username,
        String nickname,
        Integer accountRole,
        String accountRoleText,
        Integer isDefault,
        Integer status,
        LocalDateTime createdAt
) {
}
