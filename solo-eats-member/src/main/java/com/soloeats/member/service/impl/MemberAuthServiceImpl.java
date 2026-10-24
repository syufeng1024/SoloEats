package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.auth.JwtUtils;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberSmsCode;
import com.soloeats.member.domain.entity.MemberUser;
import com.soloeats.member.domain.entity.MemberWallet;
import com.soloeats.member.domain.enums.SmsScene;
import com.soloeats.member.mapper.MemberSmsCodeMapper;
import com.soloeats.member.mapper.MemberUserMapper;
import com.soloeats.member.mapper.MemberWalletMapper;
import com.soloeats.member.service.MemberAuthService;
import com.soloeats.member.service.command.SmsLoginCommand;
import com.soloeats.member.service.view.SmsLoginResult;
import com.soloeats.member.support.PhoneCipher;
import com.soloeats.member.support.SmsCodeGenerator;
import com.soloeats.system.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthServiceImpl implements MemberAuthService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    private static final Duration HOUR_WINDOW = Duration.ofHours(1);
    private static final int HOUR_LIMIT = 5;
    private static final String MINUTE_KEY_PREFIX = "sms:limit:";
    private static final String HOUR_KEY_PREFIX = "sms:hour:";
    private static final String TOKEN_TYPE = "Bearer";
    private static final int STATUS_NORMAL = 0;
    private static final int REGISTER_SOURCE_PHONE = 1;

    private final MemberUserMapper memberUserMapper;
    private final MemberWalletMapper memberWalletMapper;
    private final MemberSmsCodeMapper memberSmsCodeMapper;
    private final PhoneCipher phoneCipher;
    private final SmsCodeGenerator smsCodeGenerator;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void sendSmsCode(String phone, SmsScene scene) {
        if (scene == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scene 不能为空");
        }
        String normalized = phoneCipher.normalize(phone);
        phoneCipher.requireValid(normalized);
        String phoneHash = phoneCipher.hash(normalized);

        checkSendLimit(phoneHash);

        MemberSmsCode record = new MemberSmsCode();
        record.setPhoneHash(phoneHash);
        record.setCode(smsCodeGenerator.generate());
        record.setScene(scene.code());
        record.setExpireAt(LocalDateTime.now().plus(CODE_TTL));
        record.setUsed(0);
        record.setIp(currentIp());
        memberSmsCodeMapper.insert(record);

        // 禁止打印手机号与验证码明文，只记录场景与通道模式
        log.info("member sms code issued scene={} mock={}", scene.code(), smsCodeGenerator.isMockEnabled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmsLoginResult loginBySms(SmsLoginCommand command) {
        String normalized = phoneCipher.normalize(command == null ? null : command.phone());
        phoneCipher.requireValid(normalized);
        String code = command == null ? null : command.code();
        code = code == null ? null : code.trim();
        if (code == null || code.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码不能为空");
        }

        String phoneHash = phoneCipher.hash(normalized);
        consumeSmsCode(phoneHash, code);

        Long tenantId = TenantContext.get();
        MemberUser user = findByPhoneHash(phoneHash);
        boolean newUser = false;
        if (user == null) {
            user = register(normalized, phoneHash);
            newUser = true;
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_NORMAL) {
            throw new BizException(ErrorCode.MEMBER_DISABLED);
        }

        memberUserMapper.update(null, new LambdaUpdateWrapper<MemberUser>()
                .eq(MemberUser::getId, user.getId())
                .set(MemberUser::getLastLoginAt, LocalDateTime.now()));

        UserPrincipal principal = new UserPrincipal(
                tenantId, user.getId(), null, null, null, null, Set.of(Roles.C_USER));
        String token = jwtUtils.issue(principal);

        log.info("member sms login success userId={} tenantId={} newUser={}",
                user.getId(), tenantId, newUser);
        return new SmsLoginResult(
                token,
                TOKEN_TYPE,
                jwtUtils.expireSeconds(),
                user.getId(),
                user.getNickname(),
                newUser,
                Set.of(Roles.C_USER));
    }

    /**
     * 校验并消费验证码：最新一条未用记录 → 过期判断 → 比对 → 条件更新 used=1。
     * 条件更新影响行数为 0 说明并发请求已抢先使用，按验证码无效处理。
     */
    private void consumeSmsCode(String phoneHash, String code) {
        MemberSmsCode smsCode = memberSmsCodeMapper.selectOne(new LambdaQueryWrapper<MemberSmsCode>()
                .eq(MemberSmsCode::getPhoneHash, phoneHash)
                .eq(MemberSmsCode::getScene, SmsScene.LOGIN.code())
                .eq(MemberSmsCode::getUsed, 0)
                .orderByDesc(MemberSmsCode::getId)
                .last("LIMIT 1"));
        if (smsCode == null) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        if (smsCode.getExpireAt() == null || !smsCode.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SMS_CODE_EXPIRED);
        }
        if (!code.equals(smsCode.getCode())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        if (memberSmsCodeMapper.markUsed(smsCode.getId()) == 0) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
    }

    /**
     * 自动注册：用户与钱包同一事务创建。并发首登由 uk_phone_hash 兜底，
     * 捕获 DuplicateKeyException 后重查已存在的用户。
     */
    private MemberUser register(String phone, String phoneHash) {
        MemberUser user = new MemberUser();
        user.setPhoneCipher(phoneCipher.encrypt(phone));
        user.setPhoneHash(phoneHash);
        user.setPhoneMask(phoneCipher.mask(phone));
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setGender(0);
        user.setStatus(STATUS_NORMAL);
        user.setRegisterSource(REGISTER_SOURCE_PHONE);
        user.setGrowthValue(0);
        user.setOrderCount(0);
        user.setTotalAmount(0L);
        user.setVersion(0);
        user.setDeleted(0);
        try {
            memberUserMapper.insert(user);
            MemberWallet wallet = new MemberWallet();
            wallet.setUserId(user.getId());
            wallet.setBalance(0L);
            wallet.setFrozenAmount(0L);
            wallet.setTotalRecharge(0L);
            wallet.setTotalConsume(0L);
            wallet.setVersion(0);
            memberWalletMapper.insert(wallet);
            return user;
        } catch (DuplicateKeyException ex) {
            MemberUser existing = findByPhoneHash(phoneHash);
            if (existing == null) {
                throw ex;
            }
            log.info("member first login concurrent conflict, reuse existing userId={}", existing.getId());
            return existing;
        }
    }

    private MemberUser findByPhoneHash(String phoneHash) {
        return memberUserMapper.selectOne(new LambdaQueryWrapper<MemberUser>()
                .eq(MemberUser::getPhoneHash, phoneHash)
                .last("LIMIT 1"));
    }

    /**
     * Redis 维度限流：60 秒内 1 次、1 小时内 5 次，超限抛 10005。
     * Redis 不可用时放行并告警，避免缓存故障阻断登录入口。
     */
    private void checkSendLimit(String phoneHash) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(MINUTE_KEY_PREFIX + phoneHash, "1", SEND_INTERVAL);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new BizException(ErrorCode.RATE_LIMITED);
            }
            Long count = redisTemplate.opsForValue().increment(HOUR_KEY_PREFIX + phoneHash);
            if (count != null && count == 1L) {
                redisTemplate.expire(HOUR_KEY_PREFIX + phoneHash, HOUR_WINDOW);
            }
            if (count != null && count > HOUR_LIMIT) {
                throw new BizException(ErrorCode.RATE_LIMITED);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("sms rate limit unavailable reason={}", ex.getMessage());
        }
    }

    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded)
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (ip == null) {
            return null;
        }
        return ip.length() <= 64 ? ip : ip.substring(0, 64);
    }
}
