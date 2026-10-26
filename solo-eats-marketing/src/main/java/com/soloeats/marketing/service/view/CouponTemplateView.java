package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.CouponTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板后台视图。金额单位为分，issuedQty 为已发放张数。
 */
public record CouponTemplateView(
        Long id,
        String name,
        Integer couponType,
        long thresholdAmount,
        long discountAmount,
        BigDecimal discountRate,
        Integer bearer,
        int totalQty,
        int issuedQty,
        LocalDateTime validStart,
        LocalDateTime validEnd,
        Integer validDays,
        Integer status,
        LocalDateTime createdAt
) {

    public static CouponTemplateView from(CouponTemplate template) {
        if (template == null) {
            return null;
        }
        return new CouponTemplateView(
                template.getId(),
                template.getName(),
                template.getCouponType(),
                template.getThresholdAmount() == null ? 0L : template.getThresholdAmount(),
                template.getDiscountAmount() == null ? 0L : template.getDiscountAmount(),
                template.getDiscountRate(),
                template.getBearer(),
                template.getTotalQty() == null ? 0 : template.getTotalQty(),
                template.getIssuedQty() == null ? 0 : template.getIssuedQty(),
                template.getValidStart(),
                template.getValidEnd(),
                template.getValidDays(),
                template.getStatus(),
                template.getCreatedAt());
    }
}
