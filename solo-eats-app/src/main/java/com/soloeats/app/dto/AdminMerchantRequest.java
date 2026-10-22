package com.soloeats.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 审核通过请求。initialPassword 只在本次请求内使用，禁止记录到日志。
 */
public record AdminMerchantRequest(
        @DecimalMin(value = "0.0000", message = "commissionRate 不能小于 0.0000")
        @DecimalMax(value = "0.5000", message = "commissionRate 不能大于 0.5000")
        @Digits(integer = 1, fraction = 4, message = "commissionRate 最多 4 位小数")
        BigDecimal commissionRate,

        @Min(value = 1, message = "merchantLevel 只能是 1~5")
        @Max(value = 5, message = "merchantLevel 只能是 1~5")
        Integer merchantLevel,

        @Size(max = 64, message = "adminUsername 长度不能超过 64")
        String adminUsername,

        @NotBlank(message = "initialPassword 不能为空")
        @Size(min = 8, max = 64, message = "initialPassword 长度必须是 8~64")
        String initialPassword,

        @Size(max = 512, message = "remark 长度不能超过 512")
        String remark
) {

    public record RejectRequest(
            @NotBlank(message = "remark 不能为空")
            @Size(min = 5, max = 512, message = "驳回原因必须是 5~512 字")
            String remark
    ) {
    }

    public record StatusRequest(
            @NotNull(message = "status 不能为空")
            @Min(value = 2, message = "status 只能是 2已通过 或 4已停用")
            @Max(value = 4, message = "status 只能是 2已通过 或 4已停用")
            Integer status
    ) {
    }

    public record CommissionRequest(
            @NotNull(message = "commissionRate 不能为空")
            @DecimalMin(value = "0.0000", message = "commissionRate 不能小于 0.0000")
            @DecimalMax(value = "0.5000", message = "commissionRate 不能大于 0.5000")
            @Digits(integer = 1, fraction = 4, message = "commissionRate 最多 4 位小数")
            BigDecimal commissionRate
    ) {
    }

    public record AccountAuditRequest(
            @NotNull(message = "status 不能为空")
            @Min(value = 1, message = "status 只能是 1生效 或 2停用")
            @Max(value = 2, message = "status 只能是 1生效 或 2停用")
            Integer status
    ) {
    }
}
