package com.soloeats.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 V4__system_rbac.sql 细粒度角色码与 Roles 粗粒度族码的对齐：
 * MERCHANT 放行 MERCHANT_*，ADMIN 放行 ADMIN_*，其他角色族不互相串。
 */
@ExtendWith(MockitoExtension.class)
class RoleAuthInterceptorTest {

    private RoleAuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new RoleAuthInterceptor(new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        AuthContext.clear();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("token 角色 MERCHANT_MANAGER 命中 @RequireRole(MERCHANT)：放行")
    void allowsMerchantManager() throws Exception {
        AuthContext.set(principal(Set.of("MERCHANT_MANAGER")));

        assertThat(interceptor.preHandle(request, response, handler("merchantOnly"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("token 角色 MERCHANT_CLERK 命中 @RequireRole(MERCHANT)：放行")
    void allowsMerchantClerk() throws Exception {
        AuthContext.set(principal(Set.of("MERCHANT_CLERK")));

        assertThat(interceptor.preHandle(request, response, handler("merchantOnly"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("token 角色 C_USER 不命中 @RequireRole(MERCHANT)：HTTP 403 + code 10004")
    void deniesConsumerForMerchantEndpoint() throws Exception {
        AuthContext.set(principal(Set.of(Roles.C_USER)));

        assertThat(interceptor.preHandle(request, response, handler("merchantOnly"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":10004");
    }

    @Test
    @DisplayName("token 角色 ADMIN_OPERATION 命中 @RequireRole(ADMIN)：原有行为不回退")
    void allowsAdminOperation() throws Exception {
        AuthContext.set(principal(Set.of("ADMIN_OPERATION")));

        assertThat(interceptor.preHandle(request, response, handler("adminOnly"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("精确族码仍然通过：token 角色 RIDER 命中 @RequireRole(RIDER)")
    void allowsExactRole() throws Exception {
        AuthContext.set(principal(Set.of(Roles.RIDER)));

        assertThat(interceptor.preHandle(request, response, handler("riderOnly"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("不做包含匹配：MERCHANT_FINANCE 不命中 @RequireRole(MERCHANT) 以外的串")
    void doesNotMatchBySubstring() throws Exception {
        AuthContext.set(principal(Set.of("MERCHANT_FINANCE")));

        assertThat(interceptor.preHandle(request, response, handler("adminOnly"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private UserPrincipal principal(Set<String> roles) {
        return new UserPrincipal(1L, 100L, "merchant001", 1L, null, null, roles);
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new RoleController(), methodName);
    }

    @SuppressWarnings("unused")
    static class RoleController {

        @RequireRole(Roles.MERCHANT)
        public void merchantOnly() {
        }

        @RequireRole(Roles.ADMIN)
        public void adminOnly() {
        }

        @RequireRole(Roles.RIDER)
        public void riderOnly() {
        }
    }
}
