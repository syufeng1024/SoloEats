package com.soloeats.system.tenant;

/**
 * 当前请求的租户上下文。
 *
 * <p>由 {@link TenantFilter} 在请求进入时写入、请求结束时清理；
 * {@code TenantLineInnerInterceptor} 通过 {@link #get()} 读取并自动拼接 tenant_id 条件。
 *
 * <p>禁止在 {@code @Async} 线程中直接依赖本 ThreadLocal，需要显式透传租户 ID。
 */
public final class TenantContext {

    /** 平台租户，全站默认值。 */
    public static final Long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 未设置时返回默认租户，保证拦截器始终有可用值。 */
    public static Long get() {
        Long tenantId = HOLDER.get();
        return tenantId == null ? DEFAULT_TENANT_ID : tenantId;
    }

    /** 未设置时返回 null，用于区分「显式指定」与「默认」。 */
    public static Long getOrNull() {
        return HOLDER.get();
    }

    public static void set(Long tenantId) {
        if (tenantId == null) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(tenantId);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
