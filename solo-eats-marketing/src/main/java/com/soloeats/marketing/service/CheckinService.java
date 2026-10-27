package com.soloeats.marketing.service;

import com.soloeats.marketing.service.view.CheckinResultView;
import com.soloeats.marketing.service.view.CheckinStatusView;

import java.time.LocalDate;

/**
 * 签到与补签。签到积分走 PointService(source=2)，成长值走 MemberLevelService(source=2)。
 */
public interface CheckinService {

    /** 今日签到。同一天重复签到抛 40207。 */
    CheckinResultView checkin(Long userId);

    /** 补签最近 7 天内未签到的日期；每自然月最多 2 次。 */
    CheckinResultView compensate(Long userId, LocalDate date);

    /** 今日是否已签、连续天数、本月补签次数与可补签区间。 */
    CheckinStatusView status(Long userId);
}
