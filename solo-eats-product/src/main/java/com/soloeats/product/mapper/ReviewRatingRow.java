package com.soloeats.product.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店评分聚合结果（近 90 天、已审核通过的评价）。
 *
 * <p>无数据时 {@code reviewCount} 为 0 且各 AVG 为 null。
 */
@Data
public class ReviewRatingRow {

    private Long reviewCount;

    private BigDecimal avgOverall;

    private BigDecimal avgTaste;

    private BigDecimal avgPackage;

    private BigDecimal avgDelivery;
}
