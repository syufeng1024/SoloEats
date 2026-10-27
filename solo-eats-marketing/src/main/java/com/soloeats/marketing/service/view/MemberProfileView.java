package com.soloeats.marketing.service.view;

/**
 * C 端会员档案。
 *
 * @param growth        近 365 天有效成长值合计（含负值）
 * @param nextThreshold 下一档门槛；已是最高档时为 0
 * @param growthToNext  距下一档还差的成长值；已是最高档时为 0
 * @param maxLevel      是否已是最高档
 * @param benefitsJson  当前等级权益 JSON，无等级时为 null
 */
public record MemberProfileView(
        Long userId,
        Long levelId,
        String levelCode,
        String levelName,
        Integer levelNo,
        Integer star,
        Integer growth,
        Integer nextThreshold,
        Integer growthToNext,
        boolean maxLevel,
        String benefitsJson
) {
}
