package com.soloeats.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    @Mock
    private PermissionCache permissionCache;

    private PermissionInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new PermissionInterceptor(permissionCache, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        AuthContext.clear();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("账号用户命中权限码：放行")
    void allowsWhenPermissionCached() throws Exception {
        AuthContext.set(new UserPrincipal(1L, 9L, "operator", null, null, null,
                Set.of("ADMIN_OPERATION")));
        when(permissionCache.has(9L, "system:user:list")).thenReturn(true);

        assertThat(interceptor.preHandle(request, response, handler("listUsers"))).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(permissionCache).has(9L, "system:user:list");
    }

    @Test
    @DisplayName("账号用户缺少权限码：HTTP 403 + code 10004")
    void deniesWhenPermissionMissing() throws Exception {
        AuthContext.set(new UserPrincipal(1L, 9L, "operator", null, null, null,
                Set.of("ADMIN_OPERATION")));
        when(permissionCache.has(9L, "system:user:delete")).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, handler("deleteUser"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":10004");
    }

    @Test
    @DisplayName("未登录：HTTP 401 + code 10003")
    void deniesWithoutPrincipal() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("listUsers"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":10003");
        verifyNoInteractions(permissionCache);
    }

    @Test
    @DisplayName("旧契约 mock 登录 token（无 username）：只受 @RequireRole 约束，不查权限缓存")
    void skipsLegacyMockToken() throws Exception {
        AuthContext.set(new UserPrincipal(null, 0L, null, null, null, null, Set.of(Roles.ADMIN)));

        assertThat(interceptor.preHandle(request, response, handler("deleteUser"))).isTrue();
        verifyNoInteractions(permissionCache);
    }

    @Test
    @DisplayName("没有 @RequirePermission 的接口不做权限码校验")
    void skipsUnannotatedEndpoint() throws Exception {
        AuthContext.set(new UserPrincipal(1L, 9L, "operator", null, null, null,
                Set.of("ADMIN_OPERATION")));

        assertThat(interceptor.preHandle(request, response, handler("plain"))).isTrue();
        verifyNoInteractions(permissionCache);
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Object bean = "plain".equals(methodName) ? new PlainController() : new PermissionController();
        return new HandlerMethod(bean, methodName);
    }

    @SuppressWarnings("unused")
    static class PermissionController {

        @RequirePermission("system:user:list")
        public void listUsers() {
        }

        @RequirePermission("system:user:delete")
        public void deleteUser() {
        }
    }

    @SuppressWarnings("unused")
    static class PlainController {

        public void plain() {
        }
    }
}
