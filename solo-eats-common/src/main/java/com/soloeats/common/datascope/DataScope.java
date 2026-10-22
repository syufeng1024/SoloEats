package com.soloeats.common.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台后台的行级数据权限。标注在 Mapper 查询方法上，由 {@link DataScopeInnerInterceptor}
 * 在 SQL 进入分页插件前追加 WHERE 条件。
 *
 * <p>五档过滤只对平台后台角色（角色码以 {@code ADMIN} 开头）生效；商家中心与骑手端由
 * {@code PermissionGuard} 的 merchant_id / shop_id / user_id 校验兜底，两套机制不重叠。
 *
 * <p>示例：
 * <pre>{@code
 * @DataScope(deptAlias = "o", userAlias = "o")
 * @Select("SELECT * FROM t_order o ${ew.customSqlSegment}")
 * Page<Order> selectAdminPage(Page<Order> page, @Param(Constants.WRAPPER) Wrapper<Order> wrapper);
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 承载 tenant_id / merchant_id / shop_id 的表别名（如 {@code "o"}）。
     * 单表查询可留空，此时条件列不带表前缀。
     */
    String deptAlias() default "";

    /**
     * 承载 user_id / rider_id 的表别名。留空时复用 {@link #deptAlias()}。
     */
    String userAlias() default "";

    /**
     * 自定义过滤条件（不含 WHERE）。非空时直接使用，并忽略五档自动拼接，
     * 只允许写死在注解里的常量，禁止拼接请求参数。
     */
    String customSql() default "";

    /** 「本租户」条件列。 */
    String tenantColumn() default "tenant_id";

    /** 「本商家」条件列。 */
    String merchantColumn() default "merchant_id";

    /** 「本门店」条件列。 */
    String shopColumn() default "shop_id";

    /** 「本人」条件列。t_sys_user 这类以主键标识本人的表可配成 {@code id}。 */
    String userColumn() default "user_id";

    /** 骑手「本人」条件列，当前身份带 riderId 时优先于 {@link #userColumn()}。 */
    String riderColumn() default "rider_id";
}
