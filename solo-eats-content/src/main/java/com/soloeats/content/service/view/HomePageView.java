package com.soloeats.content.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页页面视图。后台返回 floors；用户端额外带生效中的 HOME_TOP 广告。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomePageView(
        Long id,
        String pageCode,
        String pageName,
        Integer status,
        Integer version,
        LocalDateTime publishAt,
        List<BannerView> banners,
        List<FloorView> floors
) {
}
