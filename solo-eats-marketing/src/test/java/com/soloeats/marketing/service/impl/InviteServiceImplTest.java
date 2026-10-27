package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.InviteRecord;
import com.soloeats.marketing.mapper.InviteRecordMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.support.InviteCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 邀请有礼单测。邀请码由 userId 确定性编码；防刷降级为同一邀请人 24h ≤10 次。
 */
@ExtendWith(MockitoExtension.class)
class InviteServiceImplTest {

    private static final long INVITEE_ID = 4001L;
    private static final long INVITER_ID = 4002L;

    @Mock
    private InviteRecordMapper inviteRecordMapper;

    @Mock
    private MemberUserGrowthMapper memberUserGrowthMapper;

    @Mock
    private PointService pointService;

    @Mock
    private MemberLevelService memberLevelService;

    @InjectMocks
    private InviteServiceImpl inviteService;

    @Test
    @DisplayName("邀请码稳定：同一用户两次调用返回同一码，可还原 userId")
    void inviteCodeIsDeterministic() {
        when(memberUserGrowthMapper.selectActiveUserId(INVITEE_ID)).thenReturn(INVITEE_ID);

        String first = inviteService.inviteCode(INVITEE_ID);
        String second = inviteService.inviteCode(INVITEE_ID);

        assertThat(first).isEqualTo(second);
        assertThat(InviteCodec.decode(first)).isEqualTo(INVITEE_ID);
        assertThat(first.length()).isLessThanOrEqualTo(16);
    }

    @Test
    @DisplayName("正常绑定：写 status=0 记录，奖励 50 积分 + 50 成长值")
    void bindCreatesPendingRecord() {
        String code = InviteCodec.encode(INVITER_ID);
        when(memberUserGrowthMapper.lockActiveUser(INVITER_ID)).thenReturn(INVITER_ID);
        when(memberUserGrowthMapper.selectActiveUserId(INVITEE_ID)).thenReturn(INVITEE_ID);
        when(inviteRecordMapper.countBindsSince(eq(INVITER_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(inviteRecordMapper.selectByInvitee(INVITEE_ID)).thenReturn(null);
        when(inviteRecordMapper.insert(any(InviteRecord.class))).thenAnswer(invocation -> {
            ((InviteRecord) invocation.getArgument(0)).setId(7001L);
            return 1;
        });

        inviteService.bind(INVITEE_ID, code);

        ArgumentCaptor<InviteRecord> captor = ArgumentCaptor.forClass(InviteRecord.class);
        verify(inviteRecordMapper).insert(captor.capture());
        InviteRecord saved = captor.getValue();
        assertThat(saved.getInviterId()).isEqualTo(INVITER_ID);
        assertThat(saved.getInviteeId()).isEqualTo(INVITEE_ID);
        assertThat(saved.getInviteCode()).isEqualTo(code);
        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getRewardPoint()).isEqualTo(50);
        assertThat(saved.getRewardGrowth()).isEqualTo(50);
    }

    @Test
    @DisplayName("绑定自己的邀请码：40208，不访问 Mapper")
    void bindSelfRejected() {
        String ownCode = InviteCodec.encode(INVITEE_ID);

        assertThatThrownBy(() -> inviteService.bind(INVITEE_ID, ownCode))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.INVITE_INVALID.getCode()));

        verifyNoInteractions(inviteRecordMapper, memberUserGrowthMapper,
                pointService, memberLevelService);
    }

    @Test
    @DisplayName("重复绑定（预检查命中）：40208，不插入")
    void bindDuplicateRejectedByPrecheck() {
        String code = InviteCodec.encode(INVITER_ID);
        when(memberUserGrowthMapper.lockActiveUser(INVITER_ID)).thenReturn(INVITER_ID);
        when(memberUserGrowthMapper.selectActiveUserId(INVITEE_ID)).thenReturn(INVITEE_ID);
        when(inviteRecordMapper.countBindsSince(eq(INVITER_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(inviteRecordMapper.selectByInvitee(INVITEE_ID)).thenReturn(new InviteRecord());

        assertThatThrownBy(() -> inviteService.bind(INVITEE_ID, code))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.INVITE_INVALID.getCode()));

        verify(inviteRecordMapper, never()).insert(any(InviteRecord.class));
    }

    @Test
    @DisplayName("重复绑定（uk_invitee 并发兜底）：40208")
    void bindDuplicateRejectedByUniqueKey() {
        String code = InviteCodec.encode(INVITER_ID);
        when(memberUserGrowthMapper.lockActiveUser(INVITER_ID)).thenReturn(INVITER_ID);
        when(memberUserGrowthMapper.selectActiveUserId(INVITEE_ID)).thenReturn(INVITEE_ID);
        when(inviteRecordMapper.countBindsSince(eq(INVITER_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(inviteRecordMapper.selectByInvitee(INVITEE_ID)).thenReturn(null);
        when(inviteRecordMapper.insert(any(InviteRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_invitee"));

        assertThatThrownBy(() -> inviteService.bind(INVITEE_ID, code))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.INVITE_INVALID.getCode()));
    }

    @Test
    @DisplayName("同一邀请人 24h 已达 10 次：10005 RATE_LIMITED，不插入")
    void bindRejectedByAntiAbuseLimit() {
        String code = InviteCodec.encode(INVITER_ID);
        when(memberUserGrowthMapper.lockActiveUser(INVITER_ID)).thenReturn(INVITER_ID);
        when(memberUserGrowthMapper.selectActiveUserId(INVITEE_ID)).thenReturn(INVITEE_ID);
        when(inviteRecordMapper.countBindsSince(eq(INVITER_ID), any(LocalDateTime.class)))
                .thenReturn(10);

        assertThatThrownBy(() -> inviteService.bind(INVITEE_ID, code))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.RATE_LIMITED.getCode()));

        verify(inviteRecordMapper, never()).insert(any(InviteRecord.class));
    }

    @Test
    @DisplayName("首单结算：status 0→1，给邀请人发 50 积分 + 50 成长值")
    void settleRewardAwardsInviter() {
        InviteRecord record = pendingRecord();
        when(inviteRecordMapper.selectByInvitee(INVITEE_ID)).thenReturn(record);
        when(inviteRecordMapper.markCompleted(eq(INVITEE_ID), any(LocalDateTime.class)))
                .thenReturn(1);

        int result = inviteService.settleReward(INVITEE_ID);

        assertThat(result).isEqualTo(1);
        verify(pointService).earn(INVITER_ID, 4, 7001L, 50, "邀请首单奖励");
        verify(memberLevelService).addGrowth(INVITER_ID, 4, 7001L, 50, "邀请首单奖励");
    }

    @Test
    @DisplayName("首单结算幂等：条件更新 0 行返回 0，不重复发奖")
    void settleRewardIsIdempotent() {
        InviteRecord record = pendingRecord();
        when(inviteRecordMapper.selectByInvitee(INVITEE_ID)).thenReturn(record);
        when(inviteRecordMapper.markCompleted(eq(INVITEE_ID), any(LocalDateTime.class)))
                .thenReturn(0);

        int result = inviteService.settleReward(INVITEE_ID);

        assertThat(result).isZero();
        verifyNoInteractions(pointService, memberLevelService);
    }

    private static InviteRecord pendingRecord() {
        InviteRecord record = new InviteRecord();
        record.setId(7001L);
        record.setInviterId(INVITER_ID);
        record.setInviteeId(INVITEE_ID);
        record.setStatus(0);
        record.setRewardPoint(50);
        record.setRewardGrowth(50);
        return record;
    }
}
