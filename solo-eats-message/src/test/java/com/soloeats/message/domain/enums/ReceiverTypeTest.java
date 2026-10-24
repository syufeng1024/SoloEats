package com.soloeats.message.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4__system_rbac.sql 的种子角色是细粒度角色码（MERCHANT_MANAGER / MERCHANT_CLERK / ADMIN_OPERATION），
 * 这里锁定角色族匹配口径，防止再退回精确 contains 导致商家/平台账号 10004。
 */
class ReceiverTypeTest {

    @Test
    @DisplayName("MERCHANT_MANAGER 解析为商家(2)")
    void merchantManagerResolvesToMerchant() {
        assertThat(ReceiverType.resolve(Set.of("MERCHANT_MANAGER"))).isEqualTo(2);
    }

    @Test
    @DisplayName("MERCHANT_CLERK 解析为商家(2)")
    void merchantClerkResolvesToMerchant() {
        assertThat(ReceiverType.resolve(Set.of("MERCHANT_CLERK"))).isEqualTo(2);
    }

    @Test
    @DisplayName("ADMIN_OPERATION 解析为平台(4)")
    void adminOperationResolvesToPlatform() {
        assertThat(ReceiverType.resolve(Set.of("ADMIN_OPERATION"))).isEqualTo(4);
    }

    @Test
    @DisplayName("C_USER 解析为用户(1)")
    void cUserResolvesToUser() {
        assertThat(ReceiverType.resolve(Set.of("C_USER"))).isEqualTo(1);
    }

    @Test
    @DisplayName("RIDER 解析为骑手(3)")
    void riderResolvesToRider() {
        assertThat(ReceiverType.resolve(Set.of("RIDER"))).isEqualTo(3);
    }

    @Test
    @DisplayName("空角色集合无法识别身份，返回 null")
    void emptyRolesResolveToNull() {
        assertThat(ReceiverType.resolve(Set.of())).isNull();
        assertThat(ReceiverType.resolve(null)).isNull();
    }
}
