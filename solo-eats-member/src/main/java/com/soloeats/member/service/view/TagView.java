package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.MemberTag;

/** 标签定义 + 绑定人数。autoFlag 0 手动 1 自动，status 0 启用 1 停用。 */
public record TagView(
        Long id,
        String name,
        String ruleDesc,
        Integer autoFlag,
        Integer status,
        long memberCount
) {

    public static TagView of(MemberTag tag, long memberCount) {
        return new TagView(
                tag.getId(),
                tag.getTagName(),
                tag.getTagRule(),
                tag.getAutoTag(),
                tag.getStatus(),
                memberCount);
    }
}
