package com.soloeats.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 防重注解。P0 只阻止并发重复提交；
 * 需要“重复请求返回首次响应”时，按 TDD 使用 t_idempotency 落库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 自定义 key；为空时使用方法签名 + 参数哈希。
     */
    String key() default "";

    /**
     * 防重窗口，单位秒。
     */
    long ttlSeconds() default 86400;
}
