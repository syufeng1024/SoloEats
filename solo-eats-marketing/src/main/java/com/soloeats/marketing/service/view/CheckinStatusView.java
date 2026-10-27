package com.soloeats.marketing.service.view;

import java.time.LocalDate;

/**
 * 签到状态。未签到今日时，continuousDays 返回昨日仍在延续的连续天数。
 */
public record CheckinStatusView(
        Long userId,
        LocalDate today,
        boolean checkedInToday,
        int continuousDays,
        Integer todayPointAward,
        LocalDate lastCheckinDate,
        int monthCompensationsUsed,
        int monthCompensationLimit,
        LocalDate compensableFrom,
        LocalDate compensableTo
) {
}
