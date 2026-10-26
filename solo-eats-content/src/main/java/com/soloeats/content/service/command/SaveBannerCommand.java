package com.soloeats.content.service.command;

import java.time.LocalDateTime;

/**
 * 广告位新增/编辑。jumpType=5 时 jumpValue 必须为空；
 * 其余跳转类型必须给出非空 jumpValue。
 */
public record SaveBannerCommand(
        String slotCode,
        String title,
        String imageUrl,
        Integer jumpType,
        String jumpValue,
        Integer sort,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer status
) {
}
