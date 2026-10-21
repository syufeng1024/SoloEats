package com.soloeats.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    String key();

    int limit() default 20;

    int windowSeconds() default 60;

    RateLimitDimension dimension() default RateLimitDimension.USER;
}
