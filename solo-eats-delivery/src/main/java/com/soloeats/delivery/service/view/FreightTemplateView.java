package com.soloeats.delivery.service.view;

import java.time.LocalDateTime;
import java.util.List;

public record FreightTemplateView(
        Long id,
        String templateName,
        long baseFee,
        int baseDistanceM,
        int perStepDistanceM,
        long perStepFee,
        int maxDistanceM,
        Long freeOverAmount,
        Integer status,
        List<Rule> rules,
        LocalDateTime updatedAt
) {

    public record Rule(
            Long id,
            Integer ruleType,
            String conditionJson,
            Long feeDelta,
            Integer sort,
            Integer status
    ) {
    }
}
