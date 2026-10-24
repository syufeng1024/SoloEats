package com.soloeats.app.dto;

import com.soloeats.delivery.service.command.SaveFreightTemplateCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 运费模板维护请求。金额单位分，规则列表全量覆盖。
 */
public record SaveFreightTemplateRequest(
        @NotBlank(message = "templateName 不能为空")
        @Size(max = 64, message = "templateName 不能超过 64 个字符")
        String templateName,

        @NotNull(message = "baseFee 不能为空")
        @Min(value = 0, message = "baseFee 不能为负数")
        Long baseFee,

        @Min(value = 1, message = "baseDistanceM 必须大于 0")
        Integer baseDistanceM,

        @Min(value = 1, message = "perStepDistanceM 必须大于 0")
        Integer perStepDistanceM,

        @NotNull(message = "perStepFee 不能为空")
        @Min(value = 0, message = "perStepFee 不能为负数")
        Long perStepFee,

        @Min(value = 1, message = "maxDistanceM 必须大于 0")
        Integer maxDistanceM,

        @Min(value = 0, message = "freeOverAmount 不能为负数")
        Long freeOverAmount,

        @Min(value = 0, message = "status 只能是 0启用 或 1停用")
        @Max(value = 1, message = "status 只能是 0启用 或 1停用")
        Integer status,

        @Valid
        List<Rule> rules
) {

    public record Rule(
            @NotNull(message = "ruleType 不能为空")
            @Min(value = 1, message = "ruleType 只能是 1~5")
            @Max(value = 5, message = "ruleType 只能是 1~5")
            Integer ruleType,

            String conditionJson,

            @NotNull(message = "feeDelta 不能为空")
            @Min(value = 0, message = "feeDelta 不能为负数")
            Long feeDelta,

            Integer sort,

            @Min(value = 0, message = "规则 status 只能是 0 或 1")
            @Max(value = 1, message = "规则 status 只能是 0 或 1")
            Integer status
    ) {
    }

    public SaveFreightTemplateCommand toCommand() {
        List<SaveFreightTemplateCommand.Rule> ruleCommands = rules == null ? List.of()
                : rules.stream()
                .map(rule -> new SaveFreightTemplateCommand.Rule(
                        rule.ruleType(), rule.conditionJson(), rule.feeDelta(),
                        rule.sort(), rule.status()))
                .toList();
        return new SaveFreightTemplateCommand(
                templateName, baseFee, baseDistanceM, perStepDistanceM, perStepFee,
                maxDistanceM, freeOverAmount, status, ruleCommands);
    }
}
