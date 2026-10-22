package com.soloeats.system.service;

import com.soloeats.system.service.command.SaveMenuCommand;
import com.soloeats.system.service.view.MenuNode;

import java.util.List;

public interface SysMenuService {

    /**
     * 后台菜单树（目录/菜单，不含按钮）。menuType 为空返回目录+菜单，传 1 或 2 只返回该类型。
     */
    List<MenuNode> tree(Integer menuType);

    MenuNode create(SaveMenuCommand command);

    MenuNode update(Long id, SaveMenuCommand command);

    /**
     * 逻辑删除菜单，同时清理角色-菜单授权；存在子节点时拒绝。
     */
    void delete(Long id);
}
