package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.MemberLevel;

import java.time.LocalDateTime;

/**
 * 会员等级后台视图。benefitsJson 保持原始 JSON 字符串，避免后端二次序列化。
 */
public record MemberLevelView(
        Long id,
        String levelCode,
        String levelName,
        Integer levelNo,
        Integer growthThreshold,
        Integer star,
        String benefitsJson,
        String iconUrl,
        Integer sort,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberLevelView from(MemberLevel level) {
        if (level == null) {
            return null;
        }
        return new MemberLevelView(
                level.getId(),
                level.getLevelCode(),
                level.getLevelName(),
                level.getLevelNo(),
                level.getGrowthThreshold() == null ? 0 : level.getGrowthThreshold(),
                level.getStar() == null ? 1 : level.getStar(),
                level.getBenefitsJson(),
                level.getIconUrl(),
                level.getSort() == null ? 0 : level.getSort(),
                level.getStatus(),
                level.getCreatedAt(),
                level.getUpdatedAt());
    }
}
