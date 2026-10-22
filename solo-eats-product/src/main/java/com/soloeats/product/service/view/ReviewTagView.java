package com.soloeats.product.service.view;

/**
 * 评价标签。
 */
public record ReviewTagView(
        Long id,
        String tagName,
        Integer tagType,
        Integer sort
) {
}
