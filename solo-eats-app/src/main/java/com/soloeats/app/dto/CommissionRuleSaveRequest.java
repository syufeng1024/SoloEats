package com.soloeats.app.dto;

import com.soloeats.settlement.service.command.CommissionRuleSaveCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣规则新建/编辑请求。
 *
 * <p>ruleScope：1全局 2品类 3商家 4活动；二/三/四级必须带 refId，全局级忽略 refId。
 * commissionRate 为 4 位小数比例（如 0.0600），必须大于 0 且不超过 1。
 * minAmount / maxAmount 为订单金额区间（分），不传表示不限；effectiveTo 不传表示长期有效。
 */
public record CommissionRuleSaveRequest(
        @NotNull(message = "ruleScope 不能为空")
        @Min(value = 1, message = "ruleScope 只能是 1全局 2品类 3商家 4活动")
        @Max(value = 4, message = "ruleScope 只能是 1全局 2品类 3商家 4活动")
        Integer ruleScope,

        Long refId,

        @NotNull(message = "commissionRate 不能为空")
        @DecimalMin(value = "0.0001", message = "commissionRate 必须大于 0")
        @DecimalMax(value = "1.0000", message = "commissionRate 不能大于 1")
        BigDecimal commissionRate,

        @Min(value = 0, message = "minAmount 不能为负数")
        Long minAmount,

        @Min(value = 0, message = "maxAmount 不能为负数")
        Long maxAmount,

        LocalDateTime effectiveFrom,

        LocalDateTime effectiveTo,

        Integer priority,

        @Min(value = 0, message = "status 只能是 0停用 或 1启用")
        @Max(value = 1, message = "status 只能是 0停用 或 1启用")
        Integer status,

        @Size(max = 255, message = "remark 不能超过 255 个字符")
        String remark
) {

    /**
     * 转成结算域入参；跨层级校验（refId 必填性、金额区间、生效期先后）留在服务层。
     */
    public CommissionRuleSaveCommand toCommand() {
        return new CommissionRuleSaveCommand(
                ruleScope,
                refId,
                commissionRate,
                minAmount,
                maxAmount,
                effectiveFrom,
                effectiveTo,
                priority,
                status,
                remark
        );
    }
}
