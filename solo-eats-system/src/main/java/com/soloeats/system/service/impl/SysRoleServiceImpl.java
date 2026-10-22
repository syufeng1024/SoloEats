package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysMenu;
import com.soloeats.system.domain.entity.SysRole;
import com.soloeats.system.domain.entity.SysRoleMenu;
import com.soloeats.system.mapper.SysMenuMapper;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysRoleMenuMapper;
import com.soloeats.system.service.SysRoleService;
import com.soloeats.system.service.command.SaveRoleCommand;
import com.soloeats.system.service.view.SysRoleView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    /** TODO(T02): 接入 TenantContext 后从上下文取租户；阶段B 固定租户 1。 */
    private static final Long DEFAULT_TENANT_ID = 1L;

    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_ROLE_NAME_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public PageResult<SysRoleView> page(Integer roleType, Integer status, long pageNum, long pageSize) {
        if (roleType != null) {
            validateRoleType(roleType);
        }
        if (status != null) {
            validateStatus(status);
        }
        long normalizedPageNum = pageNum < 1L ? 1L : pageNum;
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysRole::getDeleted, 0)
                .eq(roleType != null, SysRole::getRoleType, roleType)
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getId);

        Page<SysRole> page = sysRoleMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        List<SysRoleView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRoleView create(SaveRoleCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        String roleCode = normalizeRoleCode(command.roleCode());
        String roleName = requireText(command.roleName(), MAX_ROLE_NAME_LENGTH, "roleName");
        if (command.roleType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "roleType 不能为空");
        }
        int roleType = command.roleType();
        validateRoleType(roleType);
        int dataScope = command.dataScope() == null ? 5 : command.dataScope();
        validateDataScope(dataScope);
        int sort = command.sort() == null ? 0 : command.sort();
        int status = command.status() == null ? 0 : command.status();
        validateStatus(status);
        String remark = normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark");

        assertRoleCodeAvailable(roleCode, null);

        SysRole role = new SysRole();
        role.setTenantId(DEFAULT_TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setDataScope(dataScope);
        role.setSort(sort);
        role.setStatus(status);
        role.setRemark(remark);
        role.setDeleted(0);

        try {
            sysRoleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色编码已存在");
        }
        log.info("sys role created roleId={} roleCode={}", role.getId(), roleCode);
        return toView(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRoleView update(Long id, SaveRoleCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求不能为空");
        }
        SysRole role = requireRole(id);
        String roleCode = command.roleCode() == null
                ? role.getRoleCode()
                : normalizeRoleCode(command.roleCode());
        String roleName = command.roleName() == null
                ? role.getRoleName()
                : requireText(command.roleName(), MAX_ROLE_NAME_LENGTH, "roleName");
        int roleType = command.roleType() == null ? role.getRoleType() : command.roleType();
        validateRoleType(roleType);
        int dataScope = command.dataScope() == null ? role.getDataScope() : command.dataScope();
        validateDataScope(dataScope);
        int sort = command.sort() == null ? role.getSort() : command.sort();
        int status = command.status() == null ? role.getStatus() : command.status();
        validateStatus(status);
        String remark = command.remark() == null
                ? role.getRemark()
                : normalizeOptionalText(command.remark(), MAX_REMARK_LENGTH, "remark");

        assertRoleCodeAvailable(roleCode, role.getId());

        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setDataScope(dataScope);
        role.setSort(sort);
        role.setStatus(status);
        role.setRemark(remark);

        if (sysRoleMapper.updateById(role) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色已被删除");
        }
        // roleCode 变化会影响后续签发的 JWT 角色码，已签发 token 需自然过期
        log.info("sys role updated roleId={} roleCode={}", role.getId(), roleCode);
        return toView(role);
    }

    @Override
    public List<Long> listMenuIds(Long roleId) {
        SysRole role = requireRole(roleId);
        List<Long> menuIds = sysRoleMenuMapper.selectMenuIdsByRoleId(role.getId(), DEFAULT_TENANT_ID);
        return menuIds == null ? List.of() : menuIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = requireRole(roleId);
        List<Long> normalized = normalizeMenuIds(menuIds);
        validateMenusExist(normalized);

        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysRoleMenu::getRoleId, role.getId()));
        for (Long menuId : normalized) {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setTenantId(DEFAULT_TENANT_ID);
            relation.setRoleId(role.getId());
            relation.setMenuId(menuId);
            sysRoleMenuMapper.insert(relation);
        }
        log.info("sys role menus assigned roleId={} menuCount={}", roleId, normalized.size());
    }

    private SysRole requireRole(Long id) {
        if (id == null || id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色 ID 必须是正整数");
        }
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysRole::getId, id)
                .eq(SysRole::getDeleted, 0)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void validateMenusExist(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Set<Long> existing = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getTenantId, DEFAULT_TENANT_ID)
                        .eq(SysMenu::getDeleted, 0)
                        .in(SysMenu::getId, menuIds))
                .stream()
                .map(SysMenu::getId)
                .collect(Collectors.toSet());
        for (Long menuId : menuIds) {
            if (!existing.contains(menuId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜单不存在: " + menuId);
            }
        }
    }

    private void assertRoleCodeAvailable(String roleCode, Long excludeRoleId) {
        Long count = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeRoleId != null, SysRole::getId, excludeRoleId));
        if (count != null && count > 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色编码已存在");
        }
    }

    private List<Long> normalizeMenuIds(List<Long> menuIds) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                if (menuId == null) {
                    continue;
                }
                if (menuId <= 0L) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "menuIds 必须是正整数");
                }
                normalized.add(menuId);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "roleCode 不能为空");
        }
        String value = roleCode.trim().toUpperCase();
        if (!ROLE_CODE_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "roleCode 必须是 2~64 位大写字母/数字/下划线，且以字母开头");
        }
        return value;
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

    private void validateRoleType(int roleType) {
        if (roleType < 1 || roleType > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "roleType 只能是 1平台 2商家 3骑手");
        }
    }

    private void validateDataScope(int dataScope) {
        if (dataScope < 1 || dataScope > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dataScope 只能是 1~5");
        }
    }

    private void validateStatus(int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0启用 1停用");
        }
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1L) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private SysRoleView toView(SysRole role) {
        return new SysRoleView(
                role.getId(),
                role.getTenantId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleType(),
                role.getDataScope(),
                role.getSort(),
                role.getStatus(),
                role.getRemark(),
                role.getCreatedAt()
        );
    }
}
