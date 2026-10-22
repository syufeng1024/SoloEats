package com.soloeats.system.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("未设置时返回默认租户 1，保证拦截器始终有值")
    void defaultsToPlatformTenant() {
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        assertThat(TenantContext.getOrNull()).isNull();
    }

    @Test
    @DisplayName("set 后生效，clear 后回到默认租户")
    void setAndClear() {
        TenantContext.set(9L);
        assertThat(TenantContext.get()).isEqualTo(9L);

        TenantContext.clear();
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("租户上下文按线程隔离，不串到异步线程")
    void isolatedBetweenThreads() throws InterruptedException {
        TenantContext.set(9L);

        AtomicLong observed = new AtomicLong(-1L);
        Thread worker = new Thread(() -> observed.set(TenantContext.get()));
        worker.start();
        worker.join();

        assertThat(observed.get()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
        assertThat(TenantContext.get()).isEqualTo(9L);
    }
}
