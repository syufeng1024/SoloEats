package com.soloeats.common.datascope;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeInnerInterceptorTest {

    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(11L, 44L, "operator", 22L, 33L, null, Set.of("ADMIN_OPERATION"));

    @BeforeEach
    void setAuthContext() {
        AuthContext.set(PRINCIPAL);
    }

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("标注 @DataScope 的查询：原 WHERE 加括号后再 AND 数据权限条件")
    void appendsConditionToExistingWhere() {
        DataScopeInnerInterceptor interceptor = interceptorReturning("o.tenant_id = 11");
        BoundSql boundSql = boundSql("SELECT * FROM t_order o WHERE o.status = 10");

        interceptor.beforeQuery(null, statement("selectScoped"), null, new RowBounds(), null, boundSql);

        String sql = boundSql.getSql();
        assertThat(sql).contains("o.tenant_id = 11");
        assertThat(sql).contains("o.status = 10");
        // 原条件被括号包住，避免 OR 优先级被 AND 改写
        assertThat(sql).containsPattern("\\(\\s*o\\.status = 10\\s*\\)");
    }

    @Test
    @DisplayName("没有原 WHERE：直接补条件")
    void appendsConditionWithoutWhere() {
        DataScopeInnerInterceptor interceptor = interceptorReturning("o.merchant_id = 22");
        BoundSql boundSql = boundSql("SELECT * FROM t_order o");

        interceptor.beforeQuery(null, statement("selectScoped"), null, new RowBounds(), null, boundSql);

        assertThat(boundSql.getSql()).contains("WHERE o.merchant_id = 22");
    }

    @Test
    @DisplayName("未标注 @DataScope 的方法：SQL 原样不动")
    void leavesUnannotatedStatementUntouched() {
        DataScopeInnerInterceptor interceptor = interceptorReturning("o.tenant_id = 11");
        BoundSql boundSql = boundSql("SELECT * FROM t_order o");
        String original = boundSql.getSql();

        interceptor.beforeQuery(null, statement("selectPlain"), null, new RowBounds(), null, boundSql);

        assertThat(boundSql.getSql()).isEqualTo(original);
    }

    @Test
    @DisplayName("无登录身份：不做过滤，便于定时任务与启动预热")
    void skipsWhenNoPrincipal() {
        AuthContext.clear();
        DataScopeInnerInterceptor interceptor = interceptorReturning("o.tenant_id = 11");
        BoundSql boundSql = boundSql("SELECT * FROM t_order o");
        String original = boundSql.getSql();

        interceptor.beforeQuery(null, statement("selectScoped"), null, new RowBounds(), null, boundSql);

        assertThat(boundSql.getSql()).isEqualTo(original);
    }

    private DataScopeInnerInterceptor interceptorReturning(String condition) {
        return new DataScopeInnerInterceptor((principal, dataScope) -> condition);
    }

    private MappedStatement statement(String methodName) {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn(FixtureMapper.class.getName() + "." + methodName);
        return statement;
    }

    private BoundSql boundSql(String sql) {
        return new BoundSql(new MybatisConfiguration(), sql, List.of(), null);
    }

    interface FixtureMapper {

        @DataScope(deptAlias = "o", userAlias = "o")
        void selectScoped();

        void selectPlain();
    }
}
