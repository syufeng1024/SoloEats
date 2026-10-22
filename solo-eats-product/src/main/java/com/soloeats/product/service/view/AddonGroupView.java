package com.soloeats.product.service.view;

import java.util.List;

/**
 * 加料组视图。maxSelect=0 表示不限。
 */
public record AddonGroupView(
        Long id,
        String groupName,
        Integer minSelect,
        Integer maxSelect,
        Integer sort,
        List<AddonItemView> items
) {

    public record AddonItemView(
            Long id,
            String itemName,
            Long price,
            Integer stock,
            Integer sort,
            Integer status
    ) {
    }
}
