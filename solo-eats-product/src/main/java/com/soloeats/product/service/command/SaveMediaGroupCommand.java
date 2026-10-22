package com.soloeats.product.service.command;

/**
 * 素材分组维护。
 */
public record SaveMediaGroupCommand(
        String groupName,
        Long parentId,
        Integer sort,
        Integer status
) {
}
