package com.soloeats.system.service.command;

/**
 * 菜单新增/修改。menuType：1 目录 2 菜单 3 按钮；parentId=0 表示根节点。
 */
public record SaveMenuCommand(
        Long parentId,
        String menuName,
        Integer menuType,
        String path,
        String component,
        String perms,
        String icon,
        Integer sort,
        Integer visible,
        Integer status
) {
}
