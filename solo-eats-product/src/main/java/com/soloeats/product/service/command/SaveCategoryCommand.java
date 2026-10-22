package com.soloeats.product.service.command;

/**
 * 品类保存入参。parentId=0 表示一级品类。
 */
public record SaveCategoryCommand(
        Long parentId,
        String categoryName,
        String iconUrl,
        Integer sort,
        Integer status
) {
}
