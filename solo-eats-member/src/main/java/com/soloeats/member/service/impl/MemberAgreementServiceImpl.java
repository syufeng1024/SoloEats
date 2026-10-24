package com.soloeats.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.member.domain.entity.MemberAgreementConfirm;
import com.soloeats.member.mapper.MemberAgreementConfirmMapper;
import com.soloeats.member.service.MemberAgreementService;
import com.soloeats.member.service.command.AgreementConfirmCommand;
import com.soloeats.member.service.view.AgreementStatusView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MemberAgreementServiceImpl implements MemberAgreementService {

    private static final int AGREED = 1;
    private static final int WITHDRAWN = 0;

    private final MemberAgreementConfirmMapper memberAgreementConfirmMapper;
    private final String currentVersion;

    public MemberAgreementServiceImpl(
            MemberAgreementConfirmMapper memberAgreementConfirmMapper,
            @Value("${app.member.agreement-version:2026-10-01}") String currentVersion) {
        this.memberAgreementConfirmMapper = memberAgreementConfirmMapper;
        this.currentVersion = currentVersion;
    }

    @Override
    public AgreementStatusView getStatus(Long userId) {
        MemberAgreementConfirm latest = latestRecord(userId);
        boolean agreed = latest != null
                && Integer.valueOf(AGREED).equals(latest.getAgree())
                && currentVersion.equals(latest.getAgreementVersion());
        return new AgreementStatusView(
                currentVersion,
                agreed,
                agreed ? latest.getConfirmedAt() : null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgreementStatusView confirm(Long userId, AgreementConfirmCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "协议确认参数不能为空");
        }
        String version = clean(command.agreementVersion());
        if (version == null) {
            version = currentVersion;
        }
        boolean agree = command.agree() == null || command.agree();

        MemberAgreementConfirm record = new MemberAgreementConfirm();
        record.setUserId(userId);
        record.setAgreementVersion(version);
        record.setAgree(agree ? AGREED : WITHDRAWN);
        record.setConfirmedAt(LocalDateTime.now());
        memberAgreementConfirmMapper.insert(record);

        log.info("member agreement confirmed userId={} version={} agree={}", userId, version, agree);
        return getStatus(userId);
    }

    private MemberAgreementConfirm latestRecord(Long userId) {
        return memberAgreementConfirmMapper.selectOne(
                new LambdaQueryWrapper<MemberAgreementConfirm>()
                        .eq(MemberAgreementConfirm::getUserId, userId)
                        .orderByDesc(MemberAgreementConfirm::getId)
                        .last("LIMIT 1"));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
