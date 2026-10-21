package com.soloeats.common.ratelimit;

import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return 1
            """;

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Environment environment;

    @Around("@annotation(com.soloeats.common.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimit rateLimit = signature.getMethod().getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        int limit = environment.getProperty(
                "app.rate-limit." + rateLimit.key() + ".limit",
                Integer.class,
                rateLimit.limit()
        );
        int windowSeconds = environment.getProperty(
                "app.rate-limit." + rateLimit.key() + ".window-seconds",
                Integer.class,
                rateLimit.windowSeconds()
        );
        limit = Math.max(1, limit);
        windowSeconds = Math.max(1, windowSeconds);

        String dimension = resolveDimension(rateLimit.dimension());
        String redisKey = "rl:" + rateLimit.key() + ":" + dimension;
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID();

        Long allowed;
        try {
            allowed = redisTemplate.execute(
                    SCRIPT,
                    List.of(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowSeconds * 1000L),
                    String.valueOf(limit),
                    member
            );
        } catch (Exception ex) {
            log.warn("rate limit redis unavailable, fail open key={}", redisKey, ex);
            return joinPoint.proceed();
        }

        if (allowed == null || allowed == 0L) {
            throw new BizException(ErrorCode.RATE_LIMITED);
        }
        return joinPoint.proceed();
    }

    private String resolveDimension(RateLimitDimension dimension) {
        if (dimension == RateLimitDimension.GLOBAL) {
            return "global";
        }
        if (dimension == RateLimitDimension.USER) {
            UserPrincipal principal = AuthContext.current();
            if (principal != null && principal.userId() != null) {
                return "u:" + principal.userId();
            }
        }
        return "ip:" + clientIp();
    }

    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
