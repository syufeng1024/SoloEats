package com.soloeats.product.service.command;

import java.util.List;

/**
 * 规格模板一体化提交：组 + 选项。
 */
public record SaveSpecTemplateCommand(
        String groupName,
        Integer groupType,
        Integer isRequired,
        Integer sort,
        List<Option> options
) {

    public record Option(
            String optionName,
            Long priceDelta,
            Integer sort,
            Integer status
    ) {
    }
}
