package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.Favorite;

import java.time.LocalDateTime;

/**
 * 收藏记录视图。member 模块不依赖 store/product，
 * 因此只返回 targetType + targetId，店铺名/商品名由调用端按自身接口补全。
 */
public record FavoriteView(
        Long id,
        Integer targetType,
        Long targetId,
        LocalDateTime createdAt
) {

    public static FavoriteView from(Favorite favorite) {
        return new FavoriteView(
                favorite.getId(),
                favorite.getTargetType(),
                favorite.getTargetId(),
                favorite.getCreatedAt());
    }
}
