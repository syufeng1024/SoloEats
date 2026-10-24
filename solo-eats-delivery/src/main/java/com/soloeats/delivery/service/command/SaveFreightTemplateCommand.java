package com.soloeats.delivery.service.command;

import java.util.List;

/**
 * 运费模板保存入参。规则列表按全量覆盖处理，未传视为清空。
 */
public record SaveFreightTemplateCommand(
        String templateName,
        Long baseFee,
        Integer baseDistanceM,
        Integer perStepDistanceM,
        Long perStepFee,
        Integer maxDistanceM,
        Long freeOverAmount,
        Integer status,
        List<Rule> rules
) {

    public record Rule(
            Integer ruleType,
            String conditionJson,
            Long feeDelta,
            Integer sort,
            Integer status
    ) {
    }
}
