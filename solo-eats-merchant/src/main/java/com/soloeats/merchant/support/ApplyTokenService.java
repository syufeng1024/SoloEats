package com.soloeats.merchant.support;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * 入驻申请凭证。入驻发生在账号创建之前，因此不能复用用户 JWT：
 * 单独签发 24h 一次性凭证（claim type=apply、aud=apply、applyId），
 * 只允许查询/修改该 applyId 对应的申请。
 */
@Component
public class ApplyTokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_APPLY_ID = "applyId";
    private static final String TOKEN_TYPE = "apply";
    private static final String AUDIENCE = "apply";
    private static final long EXPIRE_MILLIS = 24L * 60L * 60L * 1000L;

    private final SecretKey key;

    public ApplyTokenService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(Long applyId) {
        if (applyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "applyId 不能为空");
        }
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(applyId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MILLIS))
                .audience().add(AUDIENCE).and()
                .claim(CLAIM_TYPE, TOKEN_TYPE)
                .claim(CLAIM_APPLY_ID, applyId);
        return builder.signWith(key).compact();
    }

    /**
     * 校验并返回 applyId。任何签名、类型、过期问题统一抛 70006，不向调用方泄露细节。
     */
    public Long verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .require(CLAIM_TYPE, TOKEN_TYPE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object value = claims.get(CLAIM_APPLY_ID);
            if (value == null) {
                throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
            }
            long applyId = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
            if (applyId <= 0L) {
                throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
            }
            return applyId;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.MERCHANT_APPLY_FORBIDDEN);
        }
    }

    public long expireSeconds() {
        return EXPIRE_MILLIS / 1000L;
    }
}
