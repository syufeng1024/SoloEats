package com.soloeats.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.datascope.DataScope;
import com.soloeats.system.domain.entity.SysRole;
import com.soloeats.system.domain.entity.SysUser;
import com.soloeats.system.domain.entity.SysUserRole;
import com.soloeats.system.mapper.SysRoleMapper;
import com.soloeats.system.mapper.SysUserMapper;
import com.soloeats.system.mapper.SysUserRoleMapper;
import com.soloeats.system.service.impl.DataScopeServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    private static final DataScope PLAIN = annotation("plain");

    private static final DataScope OWN_ID = annotation("ownId");

    private static final DataScope NO_ALIAS = annotation("noAlias");

    private static final DataScope CUSTOM = annotation("custom");

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    private DataScopeServiceImpl dataScopeService;

    /** tenantId=11、userId=44、merchantId=22、shopId=33 的平台运营账号。 */
    private UserPrincipal admin;

    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
    }

    @BeforeEach
    void setUp() {
        dataScopeService = new DataScopeServiceImpl(sysUserMapper, sysUserRoleMapper, sysRoleMapper);
        admin = new UserPrincipal(11L, 44L, "operator", 22L, 33L, null, Set.of("ADMIN_OPERATION"));
    }

    @Test
    @DisplayName("档位1 全部：不追加任何条件")
    void scopeAllAddsNoCondition() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_ALL, admin, PLAIN)).isEmpty();
    }

    @Test
    @DisplayName("档位2 本租户：拼接 tenant_id = 当前租户")
    void scopeTenantFiltersByTenantId() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_TENANT, admin, PLAIN))
                .isEqualTo("o.tenant_id = 11");
    }

    @Test
    @DisplayName("档位3 本商家：拼接 merchant_id = 当前商家")
    void scopeMerchantFiltersByMerchantId() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_MERCHANT, admin, PLAIN))
                .isEqualTo("o.merchant_id = 22");
    }

    @Test
    @DisplayName("档位4 本门店：拼接 shop_id = 当前门店")
    void scopeShopFiltersByShopId() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_SHOP, admin, PLAIN))
                .isEqualTo("o.shop_id = 33");
    }

    @Test
    @DisplayName("档位5 本人：普通账号按 user_id 过滤")
    void scopeSelfFiltersByUserId() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_SELF, admin, PLAIN))
                .isEqualTo("o.user_id = 44");
    }

    @Test
    @DisplayName("档位5 本人：骑手身份优先按 rider_id 过滤")
    void scopeSelfPrefersRiderId() {
        UserPrincipal rider = new UserPrincipal(11L, 44L, "rider", null, null, 55L, Set.of("RIDER"));

        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_SELF, rider, PLAIN))
                .isEqualTo("o.rider_id = 55");
    }

    @Test
    @DisplayName("档位5 本人：注解可把「本人」列映射为主键 id")
    void scopeSelfSupportsCustomOwnerColumn() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_SELF, admin, OWN_ID))
                .isEqualTo("u.id = 44");
    }

    @Test
    @DisplayName("未配别名：条件列不带表前缀")
    void scopeWithoutAliasHasNoPrefix() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_TENANT, admin, NO_ALIAS))
                .isEqualTo("tenant_id = 11");
    }

    @Test
    @DisplayName("身份缺值不静默放行：拼 1 = 0 查不到数据")
    void missingIdentityValueDeniesAll() {
        UserPrincipal noMerchant = new UserPrincipal(11L, 44L, "operator", null, 33L, null,
                Set.of("ADMIN_OPERATION"));

        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_MERCHANT, noMerchant, PLAIN))
                .isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("customSql：注解写死的条件优先于五档拼接")
    void customSqlWins() {
        assertThat(dataScopeService.buildCondition(DataScopeService.SCOPE_ALL, admin, CUSTOM))
                .isEqualTo("o.merchant_id IN (1, 2)");
    }

    @Test
    @DisplayName("非平台后台角色：不追加条件，由 PermissionGuard 兜底")
    void nonAdminIsNotFiltered() {
        UserPrincipal merchant = new UserPrincipal(11L, 44L, "merchant1", 22L, 33L, null,
                Set.of("MERCHANT_MANAGER"));

        assertThat(dataScopeService.buildCondition(merchant, PLAIN)).isEmpty();
    }

    @Test
    @DisplayName("档位解析：多角色取最宽松（数值最小）的一档")
    void resolveScopeTakesMostPermissiveRole() {
        when(sysUserMapper.selectById(44L)).thenReturn(user(5));
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(List.of(link(9L), link(10L)));
        when(sysRoleMapper.selectList(any()))
                .thenReturn(List.of(role(4), role(2)));

        assertThat(dataScopeService.resolveDataScope(admin)).isEqualTo(DataScopeService.SCOPE_TENANT);
    }

    @Test
    @DisplayName("档位解析：账号没有角色时回落到 t_sys_user.data_scope")
    void resolveScopeFallsBackToUserScope() {
        when(sysUserMapper.selectById(44L)).thenReturn(user(4));
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of());

        assertThat(dataScopeService.resolveDataScope(admin)).isEqualTo(DataScopeService.SCOPE_SHOP);
    }

    @Test
    @DisplayName("档位解析：账号不存在时失败关闭为「本人」")
    void resolveScopeFailsClosedWhenUserMissing() {
        when(sysUserMapper.selectById(44L)).thenReturn(null);

        assertThat(dataScopeService.resolveDataScope(admin)).isEqualTo(DataScopeService.SCOPE_SELF);
    }

    private static DataScope annotation(String methodName) {
        try {
            return Fixture.class.getDeclaredMethod(methodName).getAnnotation(DataScope.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试注解缺失: " + methodName, e);
        }
    }

    private static SysUser user(Integer dataScope) {
        SysUser user = new SysUser();
        user.setId(44L);
        user.setDataScope(dataScope);
        return user;
    }

    private static SysUserRole link(Long roleId) {
        SysUserRole link = new SysUserRole();
        link.setUserId(44L);
        link.setRoleId(roleId);
        return link;
    }

    private static SysRole role(Integer dataScope) {
        SysRole role = new SysRole();
        role.setDataScope(dataScope);
        role.setStatus(0);
        role.setDeleted(0);
        return role;
    }

    private static final class Fixture {

        @DataScope(deptAlias = "o", userAlias = "o")
        public void plain() {
        }

        @DataScope(deptAlias = "u", userAlias = "u", userColumn = "id")
        public void ownId() {
        }

        @DataScope
        public void noAlias() {
        }

        @DataScope(deptAlias = "o", customSql = "o.merchant_id IN (1, 2)")
        public void custom() {
        }
    }
}
