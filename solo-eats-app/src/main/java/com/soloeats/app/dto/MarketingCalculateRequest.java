package com.soloeats.app.dto;

import com.soloeats.marketing.service.MarketingCalculateCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户端营销试算请求。goodsAmount / payableAmount / couponDiscount 单位均为分。
 * userId 不接收前端传值，由 Controller 从 AuthContext 取。
 */
public record MarketingCalculateRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId,

        @NotNull(message = "goodsAmount 不能为空")
        @Min(value = 0, message = "goodsAmount 不能为负数")
        Long goodsAmount,

        @Valid
        List<Item> items,

        @Min(value = 0, message = "couponDiscount 不能为负数")
        Long couponDiscount,

        LocalDateTime at
) {

    public record Item(
            @NotNull(message = "skuId 不能为空")
            Long skuId,

            @Min(value = 1, message = "quantity 必须大于 0")
            Integer quantity,

            @NotNull(message = "payableAmount 不能为空")
            @Min(value = 0, message = "payableAmount 不能为负数")
            Long payableAmount
    ) {
    }

    public MarketingCalculateCommand toCommand(Long userId) {
        List<MarketingCalculateCommand.MarketingItem> commandItems = items == null
                ? List.of()
                : items.stream()
                        .map(item -> new MarketingCalculateCommand.MarketingItem(
                                item.skuId(), item.quantity(), item.payableAmount()))
                        .toList();
        return new MarketingCalculateCommand(
                userId, shopId, goodsAmount, commandItems, couponDiscount, at);
    }
}
