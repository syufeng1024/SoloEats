package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.datascope.DataScope;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysRole;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.domain.entity.SysUserRole;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.mapper.SysUserRoleMapper;
import com.soloeats.system.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 档位解析查 {@code t_sys_user} 与 {@code t_sys_role}；条件拼接是纯函数，便于单测。
 *
 * <p>角色权限是「或」的关系：任一角色给了更宽的范围即可见，因此取所有启用角色中
 * 数值最小（最宽）的一档。{@code t_sys_user.data_scope} 只在该账号没有任何角色时兜底，
 * 否则新建平台账号默认的 5（本人）会把管理员自身也限制住。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private static final String DENY_ALL = "1 = 0";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public int resolveDataScope(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return SCOPE_SELF;
        }
        SysUser user = sysUserMapper.selectById(principal.userId());
        if (user == null) {
            return SCOPE_SELF;
        }

        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, principal.userId()))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return normalizeScope(user.getDataScope());
        }

        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 0)
                .eq(SysRole::getDeleted, 0));
        return roles.stream()
                .map(SysRole::getDataScope)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .map(scope -> normalizeScope(scope.intValue()))
                .orElseGet(() -> normalizeScope(user.getDataScope()));
    }

    @Override
    public String buildCondition(UserPrincipal principal, DataScope annotation) {
        if (principal == null || annotation == null) {
            return "";
        }
        // data_scope 只约束平台后台角色；商家中心与骑手端由 PermissionGuard 兜底
        if (!isPlatformAdmin(principal)) {
            return "";
        }
        return buildCondition(resolveDataScope(principal), principal, annotation);
    }

    @Override
    public String buildCondition(int scope, UserPrincipal principal, DataScope annotation) {
        if (principal == null || annotation == null) {
            return "";
        }
        String customSql = annotation.customSql();
        if (customSql != null && !customSql.isBlank()) {
            return customSql.trim();
        }
        int normalized = normalizeScope(scope);
        if (normalized == SCOPE_ALL) {
            return "";
        }

        String deptPrefix = prefix(annotation.deptAlias());
        String userAlias = isBlank(annotation.userAlias()) ? annotation.deptAlias() : annotation.userAlias();
        String userPrefix = prefix(userAlias);

        return switch (normalized) {
            case SCOPE_TENANT -> eq(deptPrefix + column(annotation.tenantColumn()), principal.tenantId());
            case SCOPE_MERCHANT -> eq(deptPrefix + column(annotation.merchantColumn()), principal.merchantId());
            case SCOPE_SHOP -> eq(deptPrefix + column(annotation.shopColumn()), principal.shopId());
            case SCOPE_SELF -> selfCondition(userPrefix, principal, annotation);
            default -> DENY_ALL;
        };
    }

    /**
     * 骑手账号按 rider_id 过滤，其余账号按 user_id 过滤；两者都缺失时失败关闭。
     */
    private String selfCondition(String prefix, UserPrincipal principal, DataScope annotation) {
        Long riderId = principal.riderId();
        if (riderId != null) {
            return eq(prefix + column(annotation.riderColumn()), riderId);
        }
        return eq(prefix + column(annotation.userColumn()), principal.userId());
    }

    private boolean isPlatformAdmin(UserPrincipal principal) {
        return principal.roles() != null && principal.roles().stream()
                .anyMatch(role -> role != null && role.startsWith(Roles.ADMIN));
    }

    /** 缺值不静默放行：拼 1 = 0，宁可查不到也不能越权。 */
    private String eq(String qualifiedColumn, Long value) {
        return value == null ? DENY_ALL : qualifiedColumn + " = " + value;
    }

    private String prefix(String alias) {
        if (isBlank(alias)) {
            return "";
        }
        return identifier(alias) + ".";
    }

    private String column(String name) {
        if (isBlank(name)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "DataScope 条件列不能为空");
        }
        return identifier(name);
    }

    /** 别名与列名都是注解里的常量，仍按标识符校验，避免拼出非法 SQL。 */
    private String identifier(String value) {
        String trimmed = value.trim();
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "DataScope 别名或列名不合法: " + value);
        }
        return trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int normalizeScope(Integer value) {
        return value == null ? SCOPE_SELF : normalizeScope(value.intValue());
    }

    private int normalizeScope(int value) {
        return value >= SCOPE_ALL && value <= SCOPE_SELF ? value : SCOPE_SELF;
    }
}
