package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysMenu;
import com.soloeats.system.domain.entity.SysRoleMenu;
import com.soloeats.system.mapper.SysMenuMapper;
import com.soloeats.system.mapper.SysRoleMenuMapper;
import com.soloeats.system.service.SysMenuService;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.tenant.TenantContext;
import com.soloeats.system.service.command.SaveMenuCommand;
import com.soloeats.system.service.view.MenuNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    /** TODO(T02): 接入 TenantContext 后从上下文取租户；阶段B 固定租户 1。 */
    private static final Long DEFAULT_TENANT_ID = 1L;

    private static final long ROOT_PARENT_ID = 0L;
    private static final int TYPE_DIRECTORY = 1;
    private static final int TYPE_MENU = 2;
    private static final int TYPE_BUTTON = 3;

    private static final int MAX_MENU_NAME_LENGTH = 64;
    private static final int MAX_PATH_LENGTH = 200;
    private static final int MAX_COMPONENT_LENGTH = 200;
    private static final int MAX_PERMS_LENGTH = 128;
    private static final int MAX_ICON_LENGTH = 64;

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysTenantService sysTenantService;

    @Override
    public List<MenuNode> tree(Integer menuType) {
        boolean onlyDirectoryOrMenu = menuType == null;
        if (menuType != null && menuType != TYPE_DIRECTORY && menuType != TYPE_MENU) {
            // 按钮(3)是操作权限，不是导航节点，不允许出现在菜单树
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "menuType 只能是 1目录 或 2菜单，按钮不属于菜单树");
        }

        // 菜单目录是平台级全局资产（tenant_id 固定 1），租户可见范围由套餐 menu_ids 收敛
        Set<Long> allowedMenuIds = sysTenantService.allowedMenuIds(TenantContext.get());
        if (allowedMenuIds != null && allowedMenuIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysMenu::getDeleted, 0)
                .in(allowedMenuIds != null, SysMenu::getId, allowedMenuIds)
                .in(onlyDirectoryOrMenu, SysMenu::getMenuType, TYPE_DIRECTORY, TYPE_MENU)
                .eq(!onlyDirectoryOrMenu, SysMenu::getMenuType, menuType)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId);

        List<SysMenu> menus = sysMenuMapper.selectList(wrapper);
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SysMenu>> childrenByParent = new LinkedHashMap<>();
        Set<Long> presentIds = new HashSet<>();
        for (SysMenu menu : menus) {
            presentIds.add(menu.getId());
        }
        for (SysMenu menu : menus) {
            childrenByParent.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }

        List<MenuNode> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            // 父节点被过滤或已删除时提升为根，避免整棵子树从后台消失
            if (!presentIds.contains(menu.getParentId())) {
                roots.add(buildNode(menu, childrenByParent, new HashSet<>()));
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuNode create(SaveMenuCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        long parentId = command.parentId() == null ? ROOT_PARENT_ID : command.parentId();
        if (parentId < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "parentId 不能为负数");
        }
        int menuType = requireMenuType(command.menuType());
        SysMenu parent = resolveParent(parentId, menuType, null);
        String menuName = requireText(command.menuName(), MAX_MENU_NAME_LENGTH, "menuName");

        SysMenu menu = new SysMenu();
        menu.setTenantId(DEFAULT_TENANT_ID);
        menu.setParentId(parent == null ? ROOT_PARENT_ID : parent.getId());
        menu.setMenuName(menuName);
        menu.setMenuType(menuType);
        menu.setPath(normalizeOptionalText(command.path(), MAX_PATH_LENGTH, "path"));
        menu.setComponent(normalizeOptionalText(command.component(), MAX_COMPONENT_LENGTH, "component"));
        menu.setPerms(normalizeOptionalText(command.perms(), MAX_PERMS_LENGTH, "perms"));
        menu.setIcon(normalizeOptionalText(command.icon(), MAX_ICON_LENGTH, "icon"));
        menu.setSort(command.sort() == null ? 0 : command.sort());
        menu.setVisible(command.visible() == null ? 0 : requireVisible(command.visible()));
        menu.setStatus(command.status() == null ? 0 : requireStatus(command.status()));
        menu.setDeleted(0);

        sysMenuMapper.insert(menu);
        log.info("sys menu created menuId={} menuType={} parentId={}",
                menu.getId(), menuType, menu.getParentId());
        return toNode(menu, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuNode update(Long id, SaveMenuCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysMenu menu = requireMenu(id);
        int menuType = command.menuType() == null ? menu.getMenuType() : requireMenuType(command.menuType());
        long parentId = command.parentId() == null ? menu.getParentId() : command.parentId();
        if (parentId < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "parentId 不能为负数");
        }
        if (parentId == menu.getId()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父级不能是自身");
        }
        SysMenu parent = resolveParent(parentId, menuType, menu.getId());

        menu.setParentId(parent == null ? ROOT_PARENT_ID : parent.getId());
        menu.setMenuName(command.menuName() == null
                ? menu.getMenuName()
                : requireText(command.menuName(), MAX_MENU_NAME_LENGTH, "menuName"));
        menu.setMenuType(menuType);
        menu.setPath(command.path() == null
                ? menu.getPath()
                : normalizeOptionalText(command.path(), MAX_PATH_LENGTH, "path"));
        menu.setComponent(command.component() == null
                ? menu.getComponent()
                : normalizeOptionalText(command.component(), MAX_COMPONENT_LENGTH, "component"));
        menu.setPerms(command.perms() == null
                ? menu.getPerms()
                : normalizeOptionalText(command.perms(), MAX_PERMS_LENGTH, "perms"));
        menu.setIcon(command.icon() == null
                ? menu.getIcon()
                : normalizeOptionalText(command.icon(), MAX_ICON_LENGTH, "icon"));
        menu.setSort(command.sort() == null ? menu.getSort() : command.sort());
        menu.setVisible(command.visible() == null ? menu.getVisible() : requireVisible(command.visible()));
        menu.setStatus(command.status() == null ? menu.getStatus() : requireStatus(command.status()));

        if (sysMenuMapper.updateById(menu) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "菜单已被删除");
        }
        log.info("sys menu updated menuId={} menuType={}", menu.getId(), menuType);
        return toNode(menu, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysMenu menu = requireMenu(id);

        Long childCount = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysMenu::getParentId, menu.getId())
                .eq(SysMenu::getDeleted, 0));
        if (childCount != null && childCount > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在子菜单，请先删除子节点");
        }

        // t_sys_role_menu 无 deleted 列：删除菜单时同步撤销授权
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysRoleMenu::getMenuId, menu.getId()));

        menu.setDeleted(1);
        if (sysMenuMapper.updateById(menu) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "菜单已被删除");
        }
        log.info("sys menu logically deleted menuId={}", id);
    }

    private SysMenu requireMenu(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "菜单 ID 必须是正整数");
        }
        SysMenu menu = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysMenu::getId, id)
                .eq(SysMenu::getDeleted, 0)
                .last("LIMIT 1"));
        if (menu == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    /**
     * 校验父节点存在、层级合法，并阻止把节点挂到自己的子孙下形成环。
     *
     * @param movingMenuId 修改场景传入当前菜单 ID，用于环路检测；新增传 null
     */
    private SysMenu resolveParent(long parentId, int menuType, Long movingMenuId) {
        if (parentId == ROOT_PARENT_ID) {
            if (menuType == TYPE_BUTTON) {
                throw new BizException(ErrorCode.PARAM_INVALID, "按钮必须挂在菜单节点下");
            }
            return null;
        }
        SysMenu parent = requireMenu(parentId);
        if (movingMenuId != null) {
            assertNotDescendant(parent, movingMenuId);
        }
        if (menuType == TYPE_BUTTON && parent.getMenuType() != TYPE_MENU) {
            throw new BizException(ErrorCode.PARAM_INVALID, "按钮的父级必须是菜单(2)");
        }
        if (menuType != TYPE_BUTTON && parent.getMenuType() != TYPE_DIRECTORY) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目录/菜单的父级必须是目录(1)");
        }
        return parent;
    }

    private void assertNotDescendant(SysMenu parent, Long movingMenuId) {
        SysMenu cursor = parent;
        int guard = 0;
        while (cursor != null && guard++ < 64) {
            if (movingMenuId.equals(cursor.getId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "父级不能是自身或其子节点");
            }
            if (cursor.getParentId() == null || cursor.getParentId() == ROOT_PARENT_ID) {
                return;
            }
            cursor = sysMenuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getTenantId, DEFAULT_TENANT_ID)
                    .eq(SysMenu::getId, cursor.getParentId())
                    .eq(SysMenu::getDeleted, 0)
                    .last("LIMIT 1"));
        }
    }

    private MenuNode buildNode(SysMenu menu,
                               Map<Long, List<SysMenu>> childrenByParent,
                               Set<Long> path) {
        if (!path.add(menu.getId())) {
            return null;
        }
        List<MenuNode> children = new ArrayList<>();
        for (SysMenu child : childrenByParent.getOrDefault(menu.getId(), List.of())) {
            if (path.contains(child.getId())) {
                continue;
            }
            MenuNode childNode = buildNode(child, childrenByParent, path);
            if (childNode != null) {
                children.add(childNode);
            }
        }
        path.remove(menu.getId());
        return toNode(menu, children);
    }

    private MenuNode toNode(SysMenu menu, List<MenuNode> children) {
        return new MenuNode(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuName(),
                menu.getMenuType(),
                menu.getPath(),
                menu.getComponent(),
                menu.getPerms(),
                menu.getIcon(),
                menu.getSort(),
                menu.getVisible(),
                menu.getStatus(),
                children
        );
    }

    private int requireMenuType(Integer menuType) {
        if (menuType == null || menuType < TYPE_DIRECTORY || menuType > TYPE_BUTTON) {
            throw new BizException(ErrorCode.PARAM_INVALID, "menuType 只能是 1目录 2菜单 3按钮");
        }
        return menuType;
    }

    private int requireVisible(int visible) {
        if (visible != 0 && visible != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "visible 只能是 0显示 1隐藏");
        }
        return visible;
    }

    private int requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
        return status;
    }

    private String requireText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String normalizeOptionalText(String value, int maxLength, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }
}
