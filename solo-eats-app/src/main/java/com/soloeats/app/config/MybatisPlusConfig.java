package com.soloeats.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.soloeats.system.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    public static final String TENANT_ID_COLUMN = "tenant_id";

    /**
     * 不含 tenant_id 或必须绕过行级隔离的表。少一张就会在运行期报
     * {@code Unknown column 'tenant_id' in 'where clause'}，新增表时必须同步维护。
     *
     * <p>① 21 张既有业务表：按 README Q1 不补 tenant_id，隔离继续依赖 merchant_id / shop_id；
     * ② 公共/框架表；
     * ③ 平台级系统表：菜单目录、套餐、租户主表。
     */
    public static final Set<String> IGNORE_TABLES = Set.of(
            // ① 21 张既有业务表
            "t_order",
            "t_order_item",
            "t_order_discount",
            "t_order_status_log",
            "t_sku",
            "t_sku_stock",
            "t_stock_lock",
            "t_stock_log",
            "t_coupon_template",
            "t_user_coupon",
            "t_user_coupon_log",
            "t_payment_order",
            "t_refund_order",
            "t_refund_item",
            "t_delivery_order",
            "t_dispatch_record",
            "t_order_exception",
            "t_settlement_bill",
            "t_settlement_item",
            "t_rider_income",
            "t_payout_order",
            // ② 公共/框架表
            "t_idempotency",
            "t_outbox_event",
            "flyway_schema_history",
            // ③ 平台级系统表
            "t_sys_menu",
            "t_sys_package",
            "t_sys_tenant");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 租户隔离必须先于分页插件：先改写 WHERE，再拼 LIMIT
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContext.get());
            }

            @Override
            public String getTenantIdColumn() {
                return TENANT_ID_COLUMN;
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName == null
                        || IGNORE_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
            }
        }));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
