package com.soloeats.app.dto;

import com.soloeats.marketing.service.command.PromotionSaveCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台满减/折扣活动新建/编辑请求。金额单位分。
 *
 * <p>status 与 usedBudget 不在本请求内：前者走 PUT /{id}/status，后者由核销链路维护。
 * 业务级校验（范围项 refType 与 scopeType 是否匹配、折扣率精度、档位互斥字段置空）
 * 统一落在 {@code PromotionAdminServiceImpl}，这里只做字段级防呆。
 */
public record PromotionSaveRequest(
        @NotNull(message = "promotionType 不能为空")
        @Min(value = 1, message = "promotionType 只能是 1 满减 / 2 折扣")
        @Max(value = 2, message = "promotionType 只能是 1 满减 / 2 折扣")
        Integer promotionType,

        @NotBlank(message = "promotionName 不能为空")
        @Size(max = 64, message = "promotionName 不能超过 64 个字符")
        String promotionName,

        @Min(value = 1, message = "bearer 只能是 1 平台 / 2 商家")
        @Max(value = 2, message = "bearer 只能是 1 平台 / 2 商家")
        Integer bearer,

        @Min(value = 1, message = "merchantId 必须是正整数")
        Long merchantId,

        @Min(value = 1, message = "shopId 必须是正整数")
        Long shopId,

        @Min(value = 1, message = "scopeType 只能是 1~5")
        @Max(value = 5, message = "scopeType 只能是 1~5")
        Integer scopeType,

        @NotNull(message = "startAt 不能为空")
        LocalDateTime startAt,

        @NotNull(message = "endAt 不能为空")
        LocalDateTime endAt,

        @Min(value = 0, message = "stackable 只能是 0 或 1")
        @Max(value = 1, message = "stackable 只能是 0 或 1")
        Integer stackable,

        Integer priority,

        @Min(value = 0, message = "totalBudget 不能为负数")
        Long totalBudget,

        @NotEmpty(message = "rules 不能为空")
        @Valid
        List<RuleItem> rules,

        @Valid
        List<ScopeRefItem> scopeRefs
) {

    public record RuleItem(
            @Min(value = 0, message = "thresholdAmount 不能为负数")
            Long thresholdAmount,

            @Min(value = 1, message = "discountAmount 必须大于 0")
            Long discountAmount,

            @DecimalMin(value = "0.0", inclusive = false, message = "discountRate 必须大于 0")
            @DecimalMax(value = "1.0", inclusive = false, message = "discountRate 必须小于 1")
            BigDecimal discountRate,

            @Min(value = 0, message = "maxDiscount 不能为负数")
            Long maxDiscount,

            Integer sort
    ) {
    }

    public record ScopeRefItem(
            @NotNull(message = "refType 不能为空")
            @Min(value = 1, message = "refType 只能是 1~5")
            @Max(value = 5, message = "refType 只能是 1~5")
            Integer refType,

            @NotNull(message = "refId 不能为空")
            @Min(value = 1, message = "refId 必须是正整数")
            Long refId
    ) {
    }

    public PromotionSaveCommand toCommand() {
        List<PromotionSaveCommand.PromotionRuleCommand> ruleCommands = rules == null
                ? List.of()
                : rules.stream()
                        .map(rule -> new PromotionSaveCommand.PromotionRuleCommand(
                                rule.thresholdAmount(), rule.discountAmount(),
                                rule.discountRate(), rule.maxDiscount(), rule.sort()))
                        .toList();
        List<PromotionSaveCommand.PromotionScopeRefCommand> scopeCommands = scopeRefs == null
                ? List.of()
                : scopeRefs.stream()
                        .map(scope -> new PromotionSaveCommand.PromotionScopeRefCommand(
                                scope.refType(), scope.refId()))
                        .toList();
        return new PromotionSaveCommand(
                promotionType, promotionName, bearer, merchantId, shopId, scopeType,
                startAt, endAt, stackable, priority, totalBudget, ruleCommands, scopeCommands);
    }
}
