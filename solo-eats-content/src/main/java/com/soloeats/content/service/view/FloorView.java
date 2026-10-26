package com.soloeats.content.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 楼层视图。后台保留原始 config；用户端按 floorType 把聚合结果放进
 * banners / shops / products / notices，避免前端再次请求。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FloorView(
        Long id,
        Long pageId,
        Integer floorType,
        String floorTypeName,
        String title,
        JsonNode config,
        Integer sort,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer audience,
        Integer status,
        List<BannerView> banners,
        List<RecommendedShopView> shops,
        List<RecommendedSpuView> products,
        List<NoticeBriefView> notices
) {

    public FloorView withBanners(List<BannerView> value) {
        return new FloorView(id, pageId, floorType, floorTypeName, title, config, sort,
                effectiveFrom, effectiveTo, audience, status, value, shops, products, notices);
    }

    public FloorView withShops(List<RecommendedShopView> value) {
        return new FloorView(id, pageId, floorType, floorTypeName, title, config, sort,
                effectiveFrom, effectiveTo, audience, status, banners, value, products, notices);
    }

    public FloorView withProducts(List<RecommendedSpuView> value) {
        return new FloorView(id, pageId, floorType, floorTypeName, title, config, sort,
                effectiveFrom, effectiveTo, audience, status, banners, shops, value, notices);
    }

    public FloorView withNotices(List<NoticeBriefView> value) {
        return new FloorView(id, pageId, floorType, floorTypeName, title, config, sort,
                effectiveFrom, effectiveTo, audience, status, banners, shops, products, value);
    }
}
