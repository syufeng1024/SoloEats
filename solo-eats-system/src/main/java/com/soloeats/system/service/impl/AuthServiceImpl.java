package com.soloeats.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.auth.JwtUtils;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.mapper.SysMenuMapper;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.service.AuthService;
import com.soloeats.system.service.command.PasswordLoginCommand;
import com.soloeats.system.service.view.LoginResult;
import com.soloeats.system.support.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** TODO(T02): 接入 TenantContext 后从上下文取租户；阶段A 固定租户 1。 */
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final String TOKEN_TYPE = "Bearer";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final PasswordHasher passwordHasher;
    private final JwtUtils jwtUtils;

    @Override
    public LoginResult login(PasswordLoginCommand command) {
        String username = normalize(command == null ? null : command.username());
        String password = command == null ? null : command.password();
        if (username == null || password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码不能为空");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, DEFAULT_TENANT_ID)
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));

        if (user == null) {
            throw new BizException(ErrorCode.AUTH_BAD_CREDENTIALS);
        }
        if (user.getStatus() == null || user.getStatus() != 0) {
            throw new BizException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTH_BAD_CREDENTIALS);
        }

        Long tenantId = user.getTenantId() == null ? DEFAULT_TENANT_ID : user.getTenantId();
        Set<String> roles = toSet(sysRoleMapper.selectRoleCodesByUserId(user.getId(), tenantId));
        Set<String> permissions = toSet(sysMenuMapper.selectPermsByUserId(user.getId(), tenantId));

        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getMerchantId(),
                user.getShopId(),
                user.getRiderId(),
                roles);
        String token = jwtUtils.issue(principal, tenantId, username);

        // 只记录非敏感定位字段，禁止输出 password / password_hash
        log.info("password login success userId={} tenantId={}", user.getId(), tenantId);
        return new LoginResult(token, TOKEN_TYPE, jwtUtils.expireSeconds(), roles, permissions);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Set<String> toSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }
}
