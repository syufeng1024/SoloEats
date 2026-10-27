package com.soloeats.marketing.service.view;

import java.time.LocalDate;

/**
 * 签到/补签结果。
 */
public record CheckinResultView(
        Long recordId,
        LocalDate checkinDate,
        int continuousDays,
        int pointAward,
        int growthAward
) {
}
