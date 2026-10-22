package com.soloeats.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限码校验。权限码不放进 JWT，由 {@link PermissionInterceptor} 比对
 * 登录时写入 Redis 的 perm:user:{userId} 集合；不匹配返回 10004。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String value();
}
