package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberSmsCode;
import com.soloeats.member.domain.entity.MemberUser;
import com.soloeats.member.domain.enums.SmsScene;
import com.soloeats.member.mapper.MemberSmsCodeMapper;
import com.soloeats.member.mapper.MemberUserMapper;
import com.soloeats.member.service.MemberProfileService;
import com.soloeats.member.service.command.ChangePhoneCommand;
import com.soloeats.member.service.command.UpdateProfileCommand;
import com.soloeats.member.service.view.MemberView;
import com.soloeats.member.support.PhoneCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int STATUS_CANCELED = 2;
    private static final int GENDER_UNKNOWN = 0;
    private static final int GENDER_FEMALE = 2;
    private static final int CANCEL_COOLING_DAYS = 7;

    private final MemberUserMapper memberUserMapper;
    private final MemberSmsCodeMapper memberSmsCodeMapper;
    private final PhoneCipher phoneCipher;

    @Override
    public MemberView getProfile(Long userId) {
        return toView(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView updateProfile(Long userId, UpdateProfileCommand command) {
        requireUser(userId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资料参数不能为空");
        }

        LambdaUpdateWrapper<MemberUser> wrapper = new LambdaUpdateWrapper<MemberUser>()
                .eq(MemberUser::getId, userId);
        boolean changed = false;

        if (command.nickname() != null) {
            String nickname = clean(command.nickname());
            if (nickname == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "昵称不能为空");
            }
            wrapper.set(MemberUser::getNickname, nickname);
            changed = true;
        }
        if (command.avatarUrl() != null) {
            wrapper.set(MemberUser::getAvatarUrl, clean(command.avatarUrl()));
            changed = true;
        }
        if (command.gender() != null) {
            if (command.gender() < GENDER_UNKNOWN || command.gender() > GENDER_FEMALE) {
                throw new BizException(ErrorCode.PARAM_INVALID, "性别取值只能是 0/1/2");
            }
            wrapper.set(MemberUser::getGender, command.gender());
            changed = true;
        }
        if (command.birthday() != null) {
            if (command.birthday().isAfter(LocalDate.now())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "生日不能晚于今天");
            }
            wrapper.set(MemberUser::getBirthday, command.birthday());
            changed = true;
        }
        if (!changed) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有需要更新的资料");
        }

        memberUserMapper.update(null, wrapper);
        log.info("member profile updated userId={}", userId);
        return getProfile(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView changePhone(Long userId, ChangePhoneCommand command) {
        MemberUser user = requireUser(userId);
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "换绑参数不能为空");
        }

        String newPhone = phoneCipher.normalize(command.newPhone());
        phoneCipher.requireValid(newPhone);
        String code = clean(command.code());
        if (code == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "验证码不能为空");
        }

        String phoneHash = phoneCipher.hash(newPhone);
        consumeSmsCode(phoneHash, SmsScene.PHONE_CHANGE, code);

        if (phoneHash.equals(user.getPhoneHash())) {
            return getProfile(userId);
        }

        try {
            int rows = memberUserMapper.update(null, new LambdaUpdateWrapper<MemberUser>()
                    .eq(MemberUser::getId, userId)
                    .set(MemberUser::getPhoneCipher, phoneCipher.encrypt(newPhone))
                    .set(MemberUser::getPhoneHash, phoneHash)
                    .set(MemberUser::getPhoneMask, phoneCipher.mask(newPhone)));
            if (rows == 0) {
                throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
            }
        } catch (DuplicateKeyException ex) {
            // 唯一键 uk_phone_hash 冲突：号码已被其他账号占用。
            // 不回显号码归属，按验证码无效返回，避免手机号枚举。
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "该手机号已被其他账号绑定");
        }

        log.info("member phone changed userId={}", userId);
        return getProfile(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyCancel(Long userId) {
        MemberUser user = requireUser(userId);
        if (user.getStatus() != null && user.getStatus() == STATUS_DISABLED) {
            throw new BizException(ErrorCode.MEMBER_DISABLED);
        }

        int rows = memberUserMapper.update(null, new LambdaUpdateWrapper<MemberUser>()
                .eq(MemberUser::getId, userId)
                .eq(MemberUser::getStatus, STATUS_NORMAL)
                .set(MemberUser::getStatus, STATUS_CANCELED)
                .set(MemberUser::getCancelApplyAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前状态不可申请注销");
        }
        log.info("member cancel applied userId={} coolingDays={}", userId, CANCEL_COOLING_DAYS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCancel(Long userId) {
        requireUser(userId);
        int rows = memberUserMapper.update(null, new LambdaUpdateWrapper<MemberUser>()
                .eq(MemberUser::getId, userId)
                .eq(MemberUser::getStatus, STATUS_CANCELED)
                .ge(MemberUser::getCancelApplyAt, LocalDateTime.now().minusDays(CANCEL_COOLING_DAYS))
                .set(MemberUser::getStatus, STATUS_NORMAL)
                .setSql("cancel_apply_at = NULL"));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "注销冷静期已过或当前状态不可撤销");
        }
        log.info("member cancel revoked userId={}", userId);
    }

    /**
     * 与阶段A登录共用同一套一次性校验：最新一条未用记录 → 过期判断 → 比对 →
     * 条件更新 used=1。场景由调用方传入，改绑只认 scene=3 的验证码。
     */
    private void consumeSmsCode(String phoneHash, SmsScene scene, String code) {
        MemberSmsCode smsCode = memberSmsCodeMapper.selectOne(new LambdaQueryWrapper<MemberSmsCode>()
                .eq(MemberSmsCode::getPhoneHash, phoneHash)
                .eq(MemberSmsCode::getScene, scene.code())
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

    private MemberUser requireUser(Long userId) {
        MemberUser user = userId == null ? null : memberUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return user;
    }

    /** phone 只取掩码；历史数据只有密文时解密后再掩码，明文不离开本方法。 */
    private String maskedPhone(MemberUser user) {
        if (StringUtils.hasText(user.getPhoneMask())) {
            return user.getPhoneMask();
        }
        if (StringUtils.hasText(user.getPhoneCipher())) {
            return phoneCipher.mask(phoneCipher.decrypt(user.getPhoneCipher()));
        }
        return null;
    }

    private MemberView toView(MemberUser user) {
        return new MemberView(
                user.getId(),
                maskedPhone(user),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getBirthday(),
                user.getStatus(),
                user.getOrderCount(),
                user.getTotalAmount(),
                user.getLastOrderAt(),
                user.getCancelApplyAt(),
                user.getCreatedAt());
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
