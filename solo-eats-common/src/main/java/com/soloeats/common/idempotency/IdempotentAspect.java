package com.soloeats.common.idempotency;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(com.soloeats.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Idempotent idempotent = signature.getMethod().getAnnotation(Idempotent.class);
        if (idempotent == null) {
            return joinPoint.proceed();
        }
        String key = buildKey(joinPoint, idempotent);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.ttlSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            redisTemplate.delete(key);
            throw ex;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (StringUtils.hasText(idempotent.key())) {
            return "idem:" + idempotent.key();
        }
        String method = joinPoint.getSignature().getDeclaringTypeName()
                + ":" + joinPoint.getSignature().getName();
        return "idem:" + method + ":" + Arrays.deepHashCode(joinPoint.getArgs());
    }
}
