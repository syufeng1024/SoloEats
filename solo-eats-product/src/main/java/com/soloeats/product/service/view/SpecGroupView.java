package com.soloeats.product.service.view;

import java.util.List;

/**
 * 规格组视图。priceDelta 可为负。
 */
public record SpecGroupView(
        Long id,
        String groupName,
        Integer groupType,
        Integer isRequired,
        Integer sort,
        List<SpecOptionView> options
) {

    public record SpecOptionView(
            Long id,
            String optionName,
            Long priceDelta,
            Integer sort,
            Integer status
    ) {
    }
}
