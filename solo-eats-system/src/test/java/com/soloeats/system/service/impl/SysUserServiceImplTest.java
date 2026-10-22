package com.soloeats.system.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.system.domain.entity.SysRole;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.domain.entity.SysUserRole;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.mapper.SysUserRoleMapper;
import com.soloeats.system.service.command.CreateUserCommand;
import com.soloeats.system.service.view.SysUserView;
import com.soloeats.system.support.PasswordHasher;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    private static final long TENANT_ID = 1L;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    private SysUserServiceImpl sysUserService;

    /**
     * 纯 Mockito 环境没有 MyBatis 启动流程，LambdaQueryWrapper 解析列名依赖的
     * TableInfo 缓存需手动注册，否则抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        sysUserService = new SysUserServiceImpl(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                new PasswordHasher());
    }

    @Test
    @DisplayName("创建商家账号缺少 merchantId：抛 PARAM_INVALID，不落库")
    void createMerchantWithoutMerchantId() {
        CreateUserCommand command = new CreateUserCommand(
                "merchant2", "Merchant@123", "商家二号", "13800002222",
                2, null, null, null, null, 3, List.of());

        assertBizCode(() -> sysUserService.create(command), ErrorCode.PARAM_INVALID);
        verifyNoInteractions(sysUserMapper, sysUserRoleMapper);
    }

    @Test
    @DisplayName("创建账号缺少 userType：抛 PARAM_INVALID，不退化为平台账号")
    void createWithoutUserType() {
        CreateUserCommand command = new CreateUserCommand(
                "merchant2", "Merchant@123", null, null,
                null, null, null, null, null, null, List.of());

        assertBizCode(() -> sysUserService.create(command), ErrorCode.PARAM_INVALID);
        verifyNoInteractions(sysUserMapper, sysUserRoleMapper);
    }

    @Test
    @DisplayName("用户名已存在：抛 PARAM_INVALID，不插入账号")
    void createWithDuplicateUsername() {
        when(sysUserMapper.selectCount(any())).thenReturn(1L);

        CreateUserCommand command = new CreateUserCommand(
                "admin", "Admin@1234", "重复账号", null,
                1, null, null, null, null, 1, List.of());

        assertBizCode(() -> sysUserService.create(command), ErrorCode.PARAM_INVALID);
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("创建成功：密码存 bcrypt，账号与角色关联同一批写入，视图手机号脱敏")
    void createSuccessfully() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role(9L, "MERCHANT_MANAGER", 2)));

        CreateUserCommand command = new CreateUserCommand(
                " merchant2 ", "Merchant@123", "商家二号", "13800002222",
                2, 1L, 1L, null, null, 3, List.of(9L));

        SysUserView view = sysUserService.create(command);

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(userCaptor.capture());
        SysUser saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("merchant2");
        assertThat(saved.getPasswordHash()).startsWith("$2a$10$").isNotEqualTo("Merchant@123");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getDeleted()).isZero();

        ArgumentCaptor<SysUserRole> relationCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper, times(1)).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getRoleId()).isEqualTo(9L);
        assertThat(relationCaptor.getValue().getTenantId()).isEqualTo(TENANT_ID);

        assertThat(view.phone()).isEqualTo("138****2222");
        assertThat(view.status()).isZero();
    }

    @Test
    @DisplayName("角色类型与账号类型不一致：抛 PARAM_INVALID，不插入账号")
    void createWithMismatchedRoleType() {
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role(1L, "ADMIN", 1)));

        CreateUserCommand command = new CreateUserCommand(
                "merchant2", "Merchant@123", "商家二号", null,
                2, 1L, null, null, null, 3, List.of(1L));

        assertBizCode(() -> sysUserService.create(command), ErrorCode.PARAM_INVALID);
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("删除账号：逻辑删除并清理账号-角色关联")
    void deleteLogicallyAndCleanRoles() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setTenantId(TENANT_ID);
        user.setUsername("merchant2");
        user.setUserType(2);
        user.setStatus(0);
        user.setDeleted(0);
        user.setVersion(0);
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        sysUserService.delete(7L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
        verify(sysUserRoleMapper).delete(any());
    }

    private SysRole role(Long id, String roleCode, int roleType) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleType(roleType);
        role.setDeleted(0);
        return role;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
