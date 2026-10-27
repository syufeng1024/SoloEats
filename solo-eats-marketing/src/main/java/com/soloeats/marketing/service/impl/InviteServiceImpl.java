package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.InviteRecord;
import com.soloeats.marketing.mapper.InviteRecordMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.InviteService;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.support.InviteCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 邀请有礼。邀请码由 userId 确定性编码，不落库。
 *
 * <p>防刷降级：当前链路没有设备/IP 上下文，不伪造这些字段，改为
 * 「同一邀请人 24 小时内成功绑定 ≤ 10 次」，并用邀请人用户行锁把并发绑定串行化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int REWARD_POINT = 50;
    private static final int REWARD_GROWTH = 50;
    private static final int BIND_LIMIT_PER_24H = 10;
    private static final int SOURCE_INVITE = 4;
    private static final int STATUS_REGISTERED = 0;

    private final InviteRecordMapper inviteRecordMapper;
    private final MemberUserGrowthMapper memberUserGrowthMapper;
    private final PointService pointService;
    private final MemberLevelService memberLevelService;

    @Override
    public String inviteCode(Long userId) {
        requireUserId(userId);
        if (memberUserGrowthMapper.selectActiveUserId(userId) == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return InviteCodec.encode(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long userId, String inviteCode) {
        requireUserId(userId);
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BizException(ErrorCode.INVITE_INVALID);
        }
        String code = inviteCode.trim();
        if (code.length() > 16) {
            throw new BizException(ErrorCode.INVITE_INVALID);
        }

        long inviterId;
        try {
            inviterId = InviteCodec.decode(code);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.INVITE_INVALID);
        }
        if (inviterId == userId) {
            throw new BizException(ErrorCode.INVITE_INVALID, "不能绑定自己的邀请码");
        }

        // 锁邀请人行：校验邀请人存在，同时串行化 24h 次数校验
        if (memberUserGrowthMapper.lockActiveUser(inviterId) == null) {
            throw new BizException(ErrorCode.INVITE_INVALID);
        }
        if (memberUserGrowthMapper.selectActiveUserId(userId) == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        int recentBinds = inviteRecordMapper.countBindsSince(inviterId, now.minusHours(24));
        if (recentBinds >= BIND_LIMIT_PER_24H) {
            throw new BizException(ErrorCode.RATE_LIMITED, "邀请过于频繁，请稍后再试");
        }
        if (inviteRecordMapper.selectByInvitee(userId) != null) {
            throw new BizException(ErrorCode.INVITE_INVALID, "已绑定过邀请人");
        }

        InviteRecord record = new InviteRecord();
        record.setInviterId(inviterId);
        record.setInviteeId(userId);
        record.setInviteCode(InviteCodec.encode(inviterId));
        record.setStatus(STATUS_REGISTERED);
        record.setRewardPoint(REWARD_POINT);
        record.setRewardGrowth(REWARD_GROWTH);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        try {
            inviteRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // uk_invitee 并发兜底：另一个请求已经完成绑定
            throw new BizException(ErrorCode.INVITE_INVALID);
        }
        log.info("invite bound inviterId={} inviteeId={} recordId={}",
                inviterId, userId, record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleReward(Long inviteeUserId) {
        requireUserId(inviteeUserId);
        InviteRecord record = inviteRecordMapper.selectByInvitee(inviteeUserId);
        if (record == null || !Integer.valueOf(STATUS_REGISTERED).equals(record.getStatus())) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        if (inviteRecordMapper.markCompleted(inviteeUserId, now) == 0) {
            // 并发或已结算：条件更新是唯一幂等闸门
            return 0;
        }

        int point = record.getRewardPoint() == null || record.getRewardPoint() <= 0
                ? REWARD_POINT : record.getRewardPoint();
        int growth = record.getRewardGrowth() == null || record.getRewardGrowth() <= 0
                ? REWARD_GROWTH : record.getRewardGrowth();

        pointService.earn(record.getInviterId(), SOURCE_INVITE, record.getId(),
                point, "邀请首单奖励");
        memberLevelService.addGrowth(record.getInviterId(), SOURCE_INVITE, record.getId(),
                growth, "邀请首单奖励");

        log.info("invite reward settled inviterId={} inviteeId={} recordId={} point={} growth={}",
                record.getInviterId(), inviteeUserId, record.getId(), point, growth);
        return 1;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 必须是正整数");
        }
    }
}
