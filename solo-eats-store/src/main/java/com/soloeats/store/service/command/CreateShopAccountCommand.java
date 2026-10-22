package com.soloeats.store.service.command;

/**
 * 门店账号。accountRole：1店长 2店员；密码只在服务层交给 SysUserService。
 */
public record CreateShopAccountCommand(
        String username,
        String password,
        String nickname,
        String phone,
        Integer accountRole
) {
}
