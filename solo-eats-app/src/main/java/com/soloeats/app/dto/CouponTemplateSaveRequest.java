package com.soloeats.app.dto;

import com.soloeats.marketing.service.command.CouponTemplateSaveCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板新建/编辑请求。
 *
 * <p>请求体里没有 issuedQty 与 status：issuedQty 只由发券条件更新维护，
 * status 走 PUT /{id}/status 独立端点。金额单位为分。
 *
 * <p>couponType 只在新建时必填，编辑时被忽略，因此这里不加 @NotNull，
 * 必填判定统一落在 {@code CouponAdminServiceImpl}。
 */
public record CouponTemplateSaveRequest(
        @NotBlank(message = "name 不能为空")
        @Size(max = 64, message = "name 不能超过 64 个字符")
        String name,

        @Min(value = 1, message = "couponType 只能是 1 满减 / 2 折扣 / 3 无门槛")
        @Max(value = 3, message = "couponType 只能是 1 满减 / 2 折扣 / 3 无门槛")
        Integer couponType,

        @Min(value = 0, message = "thresholdAmount 不能为负数")
        Long thresholdAmount,

        @Min(value = 0, message = "discountAmount 不能为负数")
        Long discountAmount,

        @DecimalMin(value = "0.0001", message = "discountRate 必须大于 0")
        @DecimalMax(value = "1.0000", message = "discountRate 不能大于 1")
        BigDecimal discountRate,

        @Min(value = 1, message = "bearer 只能是 1 平台 / 2 商家")
        @Max(value = 2, message = "bearer 只能是 1 平台 / 2 商家")
        Integer bearer,

        @Min(value = 0, message = "totalQty 不能为负数")
        Integer totalQty,

        LocalDateTime validStart,

        LocalDateTime validEnd,

        @Min(value = 1, message = "validDays 必须大于 0")
        Integer validDays
) {

    public CouponTemplateSaveCommand toCommand() {
        return new CouponTemplateSaveCommand(
                name, couponType, thresholdAmount, discountAmount, discountRate, bearer,
                totalQty, validStart, validEnd, validDays);
    }
}
