package com.soloeats.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtUtils(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    public String issue(UserPrincipal principal) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(principal.userId() == null ? "anonymous" : String.valueOf(principal.userId()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMinutes * 60_000L))
                .claim("roles", new ArrayList<>(principal.roles()));

        if (principal.userId() != null) {
            builder.claim("userId", principal.userId());
        }
        if (principal.merchantId() != null) {
            builder.claim("merchantId", principal.merchantId());
        }
        if (principal.shopId() != null) {
            builder.claim("shopId", principal.shopId());
        }
        if (principal.riderId() != null) {
            builder.claim("riderId", principal.riderId());
        }
        return builder.signWith(key).compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object rolesValue = claims.get("roles");
        Set<String> roles;
        if (rolesValue instanceof Collection<?> collection) {
            roles = collection.stream().map(String::valueOf).collect(Collectors.toSet());
        } else {
            roles = Set.of();
        }

        return new UserPrincipal(
                toLong(claims.get("userId")),
                toLong(claims.get("merchantId")),
                toLong(claims.get("shopId")),
                toLong(claims.get("riderId")),
                roles
        );
    }

    public long expireSeconds() {
        return expireMinutes * 60L;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
