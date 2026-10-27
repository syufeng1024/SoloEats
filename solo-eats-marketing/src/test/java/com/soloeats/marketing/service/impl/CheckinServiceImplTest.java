package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CheckinRecord;
import com.soloeats.marketing.mapper.CheckinRecordMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.service.view.CheckinResultView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 签到/补签单测。规则常量固定为 基础10 + min(连续天数,7)*2，成长值每次 1。
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    private static final long USER_ID = 3001L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Mock
    private CheckinRecordMapper checkinRecordMapper;

    @Mock
    private MemberUserGrowthMapper memberUserGrowthMapper;

    @Mock
    private PointService pointService;

    @Mock
    private MemberLevelService memberLevelService;

    @InjectMocks
    private CheckinServiceImpl checkinService;

    @Test
    @DisplayName("首次签到：连续 1 天，奖励 12 积分 + 1 成长值")
    void firstCheckinAwardsTwelvePoints() {
        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, TODAY.minusDays(1)))
                .thenReturn(null);
        when(checkinRecordMapper.insertIdempotent(any(CheckinRecord.class)))
                .thenAnswer(invocation -> {
                    ((CheckinRecord) invocation.getArgument(0)).setId(9001L);
                    return 1;
                });

        CheckinResultView result = checkinService.checkin(USER_ID);

        assertThat(result.recordId()).isEqualTo(9001L);
        assertThat(result.checkinDate()).isEqualTo(TODAY);
        assertThat(result.continuousDays()).isEqualTo(1);
        assertThat(result.pointAward()).isEqualTo(12);
        assertThat(result.growthAward()).isEqualTo(1);
        verify(pointService).earn(USER_ID, 2, 9001L, 12, "签到奖励 " + TODAY);
        verify(memberLevelService).addGrowth(USER_ID, 2, 9001L, 1, "签到奖励 " + TODAY);
    }

    @Test
    @DisplayName("次日签到：昨日连续 1 天 → 连续 2 天，奖励 14")
    void nextDayCheckinContinuesStreak() {
        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, TODAY.minusDays(1)))
                .thenReturn(record(TODAY.minusDays(1), 1));
        when(checkinRecordMapper.insertIdempotent(any(CheckinRecord.class)))
                .thenAnswer(invocation -> {
                    ((CheckinRecord) invocation.getArgument(0)).setId(9002L);
                    return 1;
                });

        CheckinResultView result = checkinService.checkin(USER_ID);

        assertThat(result.continuousDays()).isEqualTo(2);
        assertThat(result.pointAward()).isEqualTo(14);
        verify(pointService).earn(USER_ID, 2, 9002L, 14, "签到奖励 " + TODAY);
        verify(memberLevelService).addGrowth(USER_ID, 2, 9002L, 1, "签到奖励 " + TODAY);
    }

    @Test
    @DisplayName("连续 3 天：奖励 10 + min(3,7)*2 = 16")
    void thirdDayAwardIsSixteen() {
        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, TODAY.minusDays(1)))
                .thenReturn(record(TODAY.minusDays(1), 2));
        when(checkinRecordMapper.insertIdempotent(any(CheckinRecord.class)))
                .thenAnswer(invocation -> {
                    ((CheckinRecord) invocation.getArgument(0)).setId(9003L);
                    return 1;
                });

        CheckinResultView result = checkinService.checkin(USER_ID);

        assertThat(result.continuousDays()).isEqualTo(3);
        assertThat(result.pointAward()).isEqualTo(16);
        verify(pointService).earn(USER_ID, 2, 9003L, 16, "签到奖励 " + TODAY);
    }

    @Test
    @DisplayName("同日重复签到：uk_user_date 幂等插入返回 0 → 40207，不发奖")
    void duplicateCheckinThrows40207() {
        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, TODAY.minusDays(1)))
                .thenReturn(null);
        when(checkinRecordMapper.insertIdempotent(any(CheckinRecord.class))).thenReturn(0);

        assertThatThrownBy(() -> checkinService.checkin(USER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.CHECKIN_ALREADY_DONE.getCode()));

        verifyNoInteractions(pointService, memberLevelService);
    }

    @Test
    @DisplayName("补签超过 7 天：10001 PARAM_INVALID，不落库")
    void compensateOlderThanWindowRejected() {
        assertThatThrownBy(() -> checkinService.compensate(USER_ID, TODAY.minusDays(8)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));

        verifyNoInteractions(checkinRecordMapper, memberUserGrowthMapper,
                pointService, memberLevelService);
    }

    @Test
    @DisplayName("补签达到当月 2 次上限：10005 RATE_LIMITED，不落库")
    void compensateRejectedWhenMonthlyLimitReached() {
        LocalDate date = TODAY.minusDays(1);
        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, date)).thenReturn(null);
        when(checkinRecordMapper.countCompensationsInMonth(
                eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2);

        assertThatThrownBy(() -> checkinService.compensate(USER_ID, date))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.RATE_LIMITED.getCode()));

        verify(checkinRecordMapper, never()).insertIdempotent(any(CheckinRecord.class));
        verifyNoInteractions(pointService, memberLevelService);
    }

    @Test
    @DisplayName("补签成功：按前一日连续天数发奖，并修复后续连续天数")
    void compensateAwardsAndRepairsFollowingStreak() {
        LocalDate date = TODAY.minusDays(2);
        CheckinRecord following = record(date.plusDays(1), 1);
        following.setId(9101L);

        when(memberUserGrowthMapper.lockActiveUser(USER_ID)).thenReturn(USER_ID);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, date)).thenReturn(null);
        when(checkinRecordMapper.countCompensationsInMonth(
                eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, date.minusDays(1)))
                .thenReturn(record(date.minusDays(1), 1));
        when(checkinRecordMapper.insertIdempotent(any(CheckinRecord.class)))
                .thenAnswer(invocation -> {
                    ((CheckinRecord) invocation.getArgument(0)).setId(9004L);
                    return 1;
                });
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, date.plusDays(1)))
                .thenReturn(following);
        when(checkinRecordMapper.selectByUserAndDate(USER_ID, date.plusDays(2)))
                .thenReturn(null);
        when(checkinRecordMapper.updateContinuousDays(
                anyLong(), anyInt(), any(LocalDateTime.class))).thenReturn(1);

        CheckinResultView result = checkinService.compensate(USER_ID, date);

        assertThat(result.recordId()).isEqualTo(9004L);
        assertThat(result.checkinDate()).isEqualTo(date);
        assertThat(result.continuousDays()).isEqualTo(2);
        assertThat(result.pointAward()).isEqualTo(14);
        verify(pointService).earn(USER_ID, 2, 9004L, 14, "补签奖励 " + date);
        verify(memberLevelService).addGrowth(USER_ID, 2, 9004L, 1, "补签奖励 " + date);
        // 补签日连续 2 天，次日顺延为 3 天
        verify(checkinRecordMapper).updateContinuousDays(eq(9101L), eq(3),
                any(LocalDateTime.class));
    }

    private static CheckinRecord record(LocalDate date, int continuousDays) {
        CheckinRecord record = new CheckinRecord();
        record.setId(7000L + continuousDays);
        record.setUserId(USER_ID);
        record.setCheckinDate(date);
        record.setContinuousDays(continuousDays);
        record.setPointAward(12);
        return record;
    }
}
