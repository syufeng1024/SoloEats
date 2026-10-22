package com.soloeats.common.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCacheTest {

    private static final long USER_ID = 7L;
    private static final String KEY = "perm:user:7";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private PermissionCache permissionCache;

    @BeforeEach
    void setUp() {
        permissionCache = new PermissionCache(redisTemplate);
    }

    @Test
    @DisplayName("put：去重去空格后写入集合，并设置 30 分钟 TTL")
    void putWritesDistinctPermissionsWithTtl() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        permissionCache.put(USER_ID, List.of(
                "system:user:list",
                " system:user:create ",
                "system:user:list"));

        verify(redisTemplate).delete(KEY);
        verify(setOperations).add(KEY, "system:user:list", "system:user:create");
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("put：空集合只清旧缓存，不写空 key")
    void putEmptyOnlyEvicts() {
        permissionCache.put(USER_ID, List.of());

        verify(redisTemplate).delete(KEY);
        verifyNoInteractions(setOperations);
    }

    @Test
    @DisplayName("has：命中返回 true，未命中返回 false")
    void hasReadsCacheMembership() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(KEY, "system:user:list")).thenReturn(true);
        when(setOperations.isMember(KEY, "system:user:delete")).thenReturn(false);

        assertThat(permissionCache.has(USER_ID, "system:user:list")).isTrue();
        assertThat(permissionCache.has(USER_ID, "system:user:delete")).isFalse();
    }
}
