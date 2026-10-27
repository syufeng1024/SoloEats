package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CheckinRecord;
import com.soloeats.marketing.mapper.CheckinRecordMapper;
import com.soloeats.marketing.mapper.MemberUserGrowthMapper;
import com.soloeats.marketing.service.CheckinService;
import com.soloeats.marketing.service.MemberLevelService;
import com.soloeats.marketing.service.PointService;
import com.soloeats.marketing.service.view.CheckinResultView;
import com.soloeats.marketing.service.view.CheckinStatusView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 签到/补签。连续天数按「昨日记录 +1，否则 1」计算；奖励 = 10 + min(连续天数, 7) * 2 积分，
 * 每次另给 1 点成长值。成长值与积分是两套账户，分别走既有 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int BASE_POINT = 10;
    private static final int STEP_POINT = 2;
    private static final int MAX_STREAK_DAYS = 7;
    private static final int GROWTH_PER_CHECKIN = 1;
    private static final int COMPENSATE_WINDOW_DAYS = 7;
    private static final int MONTHLY_COMPENSATE_LIMIT = 2;
    private static final int SOURCE_CHECKIN = 2;

    private final CheckinRecordMapper checkinRecordMapper;
    private final MemberUserGrowthMapper memberUserGrowthMapper;
    private final PointService pointService;
    private final MemberLevelService memberLevelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResultView checkin(Long userId) {
        requireUserId(userId);
        lockUser(userId);

        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime now = LocalDateTime.now(ZONE);
        CheckinRecord yesterday = checkinRecordMapper.selectByUserAndDate(
                userId, today.minusDays(1));
        int streak = streakAfter(yesterday);
        int award = award(streak);

        CheckinRecord record = buildRecord(userId, today, streak, award, now);
        if (checkinRecordMapper.insertIdempotent(record) == 0) {
            throw new BizException(ErrorCode.CHECKIN_ALREADY_DONE);
        }
        Long recordId = resolveRecordId(userId, today, record);
        grant(userId, recordId, award, "签到奖励 " + today);

        log.info("checkin done userId={} date={} streak={} point={} recordId={}",
                userId, today, streak, award, recordId);
        return new CheckinResultView(recordId, today, streak, award, GROWTH_PER_CHECKIN);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResultView compensate(Long userId, LocalDate date) {
        requireUserId(userId);
        LocalDate today = LocalDate.now(ZONE);
        if (date == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "补签日期不能为空");
        }
        if (!date.isBefore(today)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "补签日期必须是过去日期");
        }
        if (date.isBefore(today.minusDays(COMPENSATE_WINDOW_DAYS))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能补签最近 7 天内的日期");
        }

        lockUser(userId);
        if (checkinRecordMapper.selectByUserAndDate(userId, date) != null) {
            throw new BizException(ErrorCode.CHECKIN_ALREADY_DONE);
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate monthStart = today.withDayOfMonth(1);
        int used = checkinRecordMapper.countCompensationsInMonth(
                userId, monthStart.atStartOfDay(), monthStart.plusMonths(1).atStartOfDay());
        if (used >= MONTHLY_COMPENSATE_LIMIT) {
            throw new BizException(ErrorCode.RATE_LIMITED, "本月补签次数已达上限");
        }

        CheckinRecord previous = checkinRecordMapper.selectByUserAndDate(
                userId, date.minusDays(1));
        int streak = streakAfter(previous);
        int award = award(streak);

        CheckinRecord record = buildRecord(userId, date, streak, award, now);
        if (checkinRecordMapper.insertIdempotent(record) == 0) {
            throw new BizException(ErrorCode.CHECKIN_ALREADY_DONE);
        }
        Long recordId = resolveRecordId(userId, date, record);
        grant(userId, recordId, award, "补签奖励 " + date);
        repairFollowingStreaks(userId, date, streak, today, now);

        log.info("checkin compensate done userId={} date={} streak={} point={} recordId={}",
                userId, date, streak, award, recordId);
        return new CheckinResultView(recordId, date, streak, award, GROWTH_PER_CHECKIN);
    }

    @Override
    public CheckinStatusView status(Long userId) {
        requireUserId(userId);
        if (memberUserGrowthMapper.selectActiveUserId(userId) == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(ZONE);
        CheckinRecord latest = checkinRecordMapper.selectLatest(userId);
        boolean checkedInToday = latest != null && today.equals(latest.getCheckinDate());

        int continuousDays = 0;
        if (latest != null
                && (today.equals(latest.getCheckinDate())
                || today.minusDays(1).equals(latest.getCheckinDate()))) {
            continuousDays = latest.getContinuousDays() == null ? 0 : latest.getContinuousDays();
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        int used = checkinRecordMapper.countCompensationsInMonth(
                userId, monthStart.atStartOfDay(), monthStart.plusMonths(1).atStartOfDay());

        return new CheckinStatusView(
                userId,
                today,
                checkedInToday,
                continuousDays,
                checkedInToday ? latest.getPointAward() : null,
                latest == null ? null : latest.getCheckinDate(),
                used,
                MONTHLY_COMPENSATE_LIMIT,
                today.minusDays(COMPENSATE_WINDOW_DAYS),
                today.minusDays(1));
    }

    // ---------------------------------------------------------------
    // 内部方法
    // ---------------------------------------------------------------

    private void lockUser(Long userId) {
        if (memberUserGrowthMapper.lockActiveUser(userId) == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private CheckinRecord buildRecord(Long userId, LocalDate date, int streak,
                                      int award, LocalDateTime now) {
        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setCheckinDate(date);
        record.setContinuousDays(streak);
        record.setPointAward(award);
        // 显式写入，保证补签识别（checkin_date < DATE(created_at)）不受 DB 时钟影响
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private Long resolveRecordId(Long userId, LocalDate date, CheckinRecord inserted) {
        if (inserted.getId() != null) {
            return inserted.getId();
        }
        CheckinRecord persisted = checkinRecordMapper.selectByUserAndDate(userId, date);
        if (persisted == null || persisted.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "签到记录创建失败");
        }
        return persisted.getId();
    }

    private void grant(Long userId, Long recordId, int award, String remark) {
        pointService.earn(userId, SOURCE_CHECKIN, recordId, award, remark);
        memberLevelService.addGrowth(userId, SOURCE_CHECKIN, recordId, GROWTH_PER_CHECKIN, remark);
    }

    private int streakAfter(CheckinRecord previous) {
        if (previous == null) {
            return 1;
        }
        int previousStreak = previous.getContinuousDays() == null ? 0 : previous.getContinuousDays();
        return previousStreak + 1;
    }

    private int award(int streak) {
        return BASE_POINT + Math.min(streak, MAX_STREAK_DAYS) * STEP_POINT;
    }

    /**
     * 补签插入后，后续已存在的连续记录需要把 continuous_days 顺延；
     * 只改连续天数，不改 point_award、不重复发奖。最多影响 7 天窗口内的记录。
     */
    private void repairFollowingStreaks(Long userId, LocalDate date, int streak,
                                        LocalDate today, LocalDateTime now) {
        int expected = streak;
        LocalDate cursor = date.plusDays(1);
        while (!cursor.isAfter(today)) {
            CheckinRecord following = checkinRecordMapper.selectByUserAndDate(userId, cursor);
            if (following == null) {
                break;
            }
            expected++;
            if (following.getContinuousDays() == null
                    || following.getContinuousDays() != expected) {
                checkinRecordMapper.updateContinuousDays(following.getId(), expected, now);
            }
            cursor = cursor.plusDays(1);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId 必须是正整数");
        }
    }
}
