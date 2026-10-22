package com.soloeats.system.service.view;

import java.util.List;

/**
 * 菜单树节点。菜单树只含目录(1)与菜单(2)，按钮(3)不进树，children 无子节点时为空列表。
 */
public record MenuNode(
        Long id,
        Long parentId,
        String menuName,
        Integer menuType,
        String path,
        String component,
        String perms,
        String icon,
        Integer sort,
        Integer visible,
        Integer status,
        List<MenuNode> children
) {
}
