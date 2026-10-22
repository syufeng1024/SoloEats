package com.soloeats.product.service.view;

import java.util.List;

/**
 * 品类树节点。children 只在一级品类上有值。
 */
public record CategoryNode(
        Long id,
        Long parentId,
        String categoryName,
        String iconUrl,
        Integer level,
        Integer sort,
        Integer status,
        List<CategoryNode> children
) {
}
