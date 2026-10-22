package com.soloeats.common.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 用户权限码缓存。key 为 perm:user:{userId}，value 为 SET，TTL 30 分钟。
 * 权限码数量可能上百条，因此不放 JWT，只在登录成功时写入。
 */
@Component
@RequiredArgsConstructor
public class PermissionCache {

    public static final String KEY_PREFIX = "perm:user:";
    public static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * 全量覆盖写入：先删旧集合，避免角色调整后残留已撤销的权限码。
     */
    public void put(Long userId, Collection<String> permissions) {
        String key = key(userId);
        redisTemplate.delete(key);
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        String[] values = permissions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toArray(String[]::new);
        if (values.length == 0) {
            return;
        }
        redisTemplate.opsForSet().add(key, values);
        redisTemplate.expire(key, TTL);
    }

    public Set<String> get(Long userId) {
        Set<String> values = redisTemplate.opsForSet().members(key(userId));
        return values == null ? Set.of() : values;
    }

    public boolean has(Long userId, String permission) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key(userId), permission));
    }

    public void evict(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return KEY_PREFIX + userId;
    }
}
