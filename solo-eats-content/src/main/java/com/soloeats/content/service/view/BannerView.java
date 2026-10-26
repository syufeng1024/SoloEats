package com.soloeats.content.service.view;

import com.soloeats.content.domain.entity.Banner;

import java.time.LocalDateTime;

public record BannerView(
        Long id,
        String slotCode,
        String title,
        String imageUrl,
        Integer jumpType,
        String jumpValue,
        Integer sort,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long clickCount,
        Long viewCount,
        Integer status
) {

    public static BannerView from(Banner banner) {
        return new BannerView(
                banner.getId(),
                banner.getSlotCode(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getJumpType(),
                banner.getJumpValue(),
                banner.getSort(),
                banner.getEffectiveFrom(),
                banner.getEffectiveTo(),
                banner.getClickCount(),
                banner.getViewCount(),
                banner.getStatus()
        );
    }
}
