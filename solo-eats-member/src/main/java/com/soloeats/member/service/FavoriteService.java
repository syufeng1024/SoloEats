package com.soloeats.member.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.member.service.view.FavoriteView;

/**
 * C 端收藏。targetType 1 店铺、2 商品；同一用户重复收藏同一对象返回成功且不重复插入。
 */
public interface FavoriteService {

    FavoriteView add(Long userId, Integer targetType, Long targetId);

    /** 取消收藏幂等：记录不存在也返回成功，不抛 404。 */
    void remove(Long userId, Integer targetType, Long targetId);

    /** targetType / targetId 为空表示不过滤；按收藏时间倒序。 */
    PageResult<FavoriteView> page(Long userId, Integer targetType, Long targetId,
                                  long pageNum, long pageSize);
}
