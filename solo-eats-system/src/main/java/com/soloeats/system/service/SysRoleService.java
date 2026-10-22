package com.soloeats.system.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.system.service.command.SaveRoleCommand;
import com.soloeats.system.service.view.SysRoleView;

import java.util.List;

public interface SysRoleService {

    PageResult<SysRoleView> page(Integer roleType, Integer status, long pageNum, long pageSize);

    SysRoleView create(SaveRoleCommand command);

    SysRoleView update(Long id, SaveRoleCommand command);

    List<Long> listMenuIds(Long roleId);

    /**
     * 全量替换角色菜单授权（先删后插，同一事务）。
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
