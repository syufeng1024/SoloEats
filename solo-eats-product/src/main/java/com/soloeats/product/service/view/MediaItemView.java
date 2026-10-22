package com.soloeats.product.service.view;

/**
 * 素材项视图。fileId 指向 t_file.id。
 */
public record MediaItemView(
        Long id,
        Long groupId,
        Long fileId,
        Integer mediaType,
        Integer sort,
        Integer status
) {
}
