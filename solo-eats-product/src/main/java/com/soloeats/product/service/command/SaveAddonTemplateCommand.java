package com.soloeats.product.service.command;

import java.util.List;

/**
 * 加料模板一体化提交：组 + 加料项。
 */
public record SaveAddonTemplateCommand(
        String groupName,
        Integer minSelect,
        Integer maxSelect,
        Integer sort,
        List<Item> items
) {

    public record Item(
            String itemName,
            Long price,
            Integer stock,
            Integer sort,
            Integer status
    ) {
    }
}
