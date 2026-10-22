package com.soloeats.product.service.view;

/**
 * 素材分组视图。
 */
public record MediaGroupView(
        Long id,
        String groupName,
        Long parentId,
        Integer sort,
        Integer status
) {
}
