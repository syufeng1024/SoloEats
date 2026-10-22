package com.soloeats.common.datascope;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行级数据权限拦截器：对标注了 {@link DataScope} 的 Mapper 方法，按当前登录身份追加 WHERE 条件。
 *
 * <p>注册顺序必须是 {@code TenantLineInnerInterceptor} → 本拦截器 → 分页插件：
 * 先由租户插件补 tenant_id，再追加数据权限，最后由分页插件拼 LIMIT。顺序颠倒会把 LIMIT
 * 拼在 WHERE 之前，或在分页子查询上重复过滤。
 *
 * <p>本类只负责「识别注解 + 改写 SQL」。档位解析与条件生成由 system 模块的
 * {@code DataScopeService} 通过构造参数注入，common 不依赖任何业务模块。
 *
 * <p>无登录身份（定时任务、启动预热）时不做过滤；非法档位与无法解析的 SQL 一律失败关闭。
 */
@Slf4j
public class DataScopeInnerInterceptor implements InnerInterceptor {

    /** 由 system 模块提供：把当前身份与注解解析成 SQL 条件（不含 WHERE），空串表示不过滤。 */
    @FunctionalInterface
    public interface ScopeConditionResolver {
        String resolve(UserPrincipal principal, DataScope dataScope);
    }

    private static final Map<String, Optional<DataScope>> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private final ScopeConditionResolver resolver;

    public DataScopeInnerInterceptor(ScopeConditionResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("ScopeConditionResolver 不能为空");
        }
        this.resolver = resolver;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) {
        DataScope dataScope = findDataScope(ms.getId());
        if (dataScope == null) {
            return;
        }
        UserPrincipal principal = AuthContext.current();
        if (principal == null) {
            return;
        }
        String condition = resolver.resolve(principal, dataScope);
        if (condition == null || condition.isBlank()) {
            return;
        }
        String rewritten = appendCondition(boundSql.getSql(), condition, ms.getId());
        PluginUtils.mpBoundSql(boundSql).sql(rewritten);
    }

    /**
     * 注解查找结果按 MappedStatement ID 缓存；反射只在首次查询时发生。
     */
    private DataScope findDataScope(String statementId) {
        if (statementId == null || statementId.isEmpty()) {
            return null;
        }
        return ANNOTATION_CACHE.computeIfAbsent(statementId, id -> Optional.ofNullable(lookup(id)))
                .orElse(null);
    }

    private DataScope lookup(String statementId) {
        int split = statementId.lastIndexOf('.');
        if (split <= 0 || split == statementId.length() - 1) {
            return null;
        }
        String className = statementId.substring(0, split);
        String methodName = statementId.substring(split + 1);
        Class<?> mapperType = loadClass(className);
        if (mapperType == null) {
            return null;
        }
        for (Method method : mapperType.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            DataScope dataScope = method.getAnnotation(DataScope.class);
            if (dataScope != null) {
                return dataScope;
            }
        }
        return null;
    }

    private Class<?> loadClass(String className) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        try {
            return Class.forName(className, false, contextLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            try {
                return Class.forName(className, false, DataScopeInnerInterceptor.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignored) {
                // XML 里自定义的 statement id 不是 Mapper 接口，按「无数据权限」处理
                return null;
            }
        }
    }

    private String appendCondition(String sql, String condition, String statementId) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            Expression conditionExpression = CCJSqlParserUtil.parseCondExpression(condition);
            if (!applyCondition(statement, conditionExpression)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "数据权限暂不支持该查询语句: " + statementId);
            }
            return statement.toString();
        } catch (JSQLParserException e) {
            log.error("data scope sql rewrite failed statementId={}", statementId, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "数据权限 SQL 改写失败: " + statementId);
        }
    }

    private boolean applyCondition(Statement statement, Expression condition) {
        if (statement instanceof PlainSelect plainSelect) {
            Expression where = plainSelect.getWhere();
            if (where == null) {
                plainSelect.setWhere(condition);
            } else {
                plainSelect.setWhere(new AndExpression(
                        new ParenthesedExpressionList<>(where),
                        new ParenthesedExpressionList<>(condition)));
            }
            return true;
        }
        if (statement instanceof SetOperationList setOperationList) {
            boolean applied = false;
            for (Select select : setOperationList.getSelects()) {
                applied |= applyCondition(select, condition);
            }
            return applied;
        }
        if (statement instanceof ParenthesedSelect parenthesedSelect) {
            return parenthesedSelect.getSelect() != null
                    && applyCondition(parenthesedSelect.getSelect(), condition);
        }
        return false;
    }
}
