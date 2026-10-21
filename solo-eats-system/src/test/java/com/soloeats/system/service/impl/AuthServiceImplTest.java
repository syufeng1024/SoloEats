package com.soloeats.system.service.impl;

import com.soloeats.common.auth.JwtUtils;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.mapper.SysMenuMapper;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.service.command.PasswordLoginCommand;
import com.soloeats.system.service.view.LoginResult;
import com.soloeats.system.support.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    /** 与 V4__system_rbac.sql 中 admin 账号的字面量保持一致。 */
    private static final String ADMIN_SEED_HASH =
            "$2a$10$p1/Ro5q89SQ.8XRMx5hiDud2acb4B2haAykz4/vEQ8NoQw4BVn39e";
    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 1L;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysMenuMapper sysMenuMapper;

    @Mock
    private JwtUtils jwtUtils;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                sysUserMapper,
                sysRoleMapper,
                sysMenuMapper,
                new PasswordHasher(),
                jwtUtils);
    }

    @Test
    @DisplayName("密码正确：签发 token 并返回角色与权限")
    void loginWithCorrectPassword() {
        when(sysUserMapper.selectOne(any())).thenReturn(user(0, ADMIN_SEED_HASH));
        when(sysRoleMapper.selectRoleCodesByUserId(USER_ID, TENANT_ID)).thenReturn(List.of("ADMIN"));
        when(sysMenuMapper.selectPermsByUserId(USER_ID, TENANT_ID))
                .thenReturn(List.of("system:user:list", "system:role:list"));
        when(jwtUtils.issue(any(UserPrincipal.class), eq(TENANT_ID), eq("admin"))).thenReturn("jwt-token");
        when(jwtUtils.expireSeconds()).thenReturn(7200L);

        LoginResult result = authService.login(new PasswordLoginCommand(" admin ", "Admin@123"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(7200L);
        assertThat(result.roles()).containsExactly("ADMIN");
        assertThat(result.permissions()).contains("system:user:list", "system:role:list");
        verify(jwtUtils).issue(any(UserPrincipal.class), eq(TENANT_ID), eq("admin"));
    }

    @Test
    @DisplayName("密码错误：抛 AUTH_BAD_CREDENTIALS")
    void loginWithWrongPassword() {
        when(sysUserMapper.selectOne(any())).thenReturn(user(0, ADMIN_SEED_HASH));

        assertBizCode(() -> authService.login(new PasswordLoginCommand("admin", "wrong-password")),
                ErrorCode.AUTH_BAD_CREDENTIALS);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("账号停用：抛 AUTH_ACCOUNT_DISABLED")
    void loginWithDisabledAccount() {
        when(sysUserMapper.selectOne(any())).thenReturn(user(1, ADMIN_SEED_HASH));

        assertBizCode(() -> authService.login(new PasswordLoginCommand("admin", "Admin@123")),
                ErrorCode.AUTH_ACCOUNT_DISABLED);
        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("账号不存在：抛 AUTH_BAD_CREDENTIALS，不泄露账号是否存在")
    void loginWithUnknownAccount() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        assertBizCode(() -> authService.login(new PasswordLoginCommand("nobody", "Admin@123")),
                ErrorCode.AUTH_BAD_CREDENTIALS);
        verifyNoInteractions(jwtUtils);
    }

    private SysUser user(int status, String passwordHash) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setUsername("admin");
        user.setPasswordHash(passwordHash);
        user.setUserType(1);
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
