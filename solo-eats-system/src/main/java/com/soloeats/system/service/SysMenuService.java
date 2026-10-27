package com.soloeats.system.service;

import com.soloeats.system.service.command.SaveMenuCommand;
import com.soloeats.system.service.view.MenuNode;

import java.util.List;

public interface SysMenuService {

    /**
     * 后台菜单树（目录/菜单，不含按钮）。menuType 为空返回目录+菜单，传 1 或 2 只返回该类型。
     */
    List<MenuNode> tree(Integer menuType);

    /**
     * 当前用户可访问的菜单树。按 t_sys_user_role → t_sys_role_menu 收敛菜单范围：
     * 只返回 status=0、deleted=0 的节点；menuType 为空时不过滤节点类型。超级管理员（role_id=1）
     * 直接返回全量树，避免未绑定 role_menu 时看不到菜单。无角色或无授权菜单返回空列表。
     */
    List<MenuNode> treeByUser(Long userId, Integer menuType);

    MenuNode create(SaveMenuCommand command);

    MenuNode update(Long id, SaveMenuCommand command);

    /**
     * 逻辑删除菜单，同时清理角色-菜单授权；存在子节点时拒绝。
     */
    void delete(Long id);
}
