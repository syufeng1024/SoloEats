package com.soloeats.app.dto;

import com.soloeats.marketing.service.command.SeckillActivitySaveCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台秒杀场次新建/编辑请求。金额单位分。
 *
 * <p>status 不在请求内：由时间窗推导；商品库存为秒杀独立库存，不占用 t_sku_stock。
 * 跨字段校验（sessionStart &lt; sessionEnd、skuId 不重复、库存不小于已售）落在
 * {@code SeckillServiceImpl}，这里只做字段级防呆。
 */
public record SeckillActivitySaveRequest(
        @NotBlank(message = "activityName 不能为空")
        @Size(max = 64, message = "activityName 不能超过 64 个字符")
        String activityName,

        @NotNull(message = "sessionStart 不能为空")
        LocalDateTime sessionStart,

        @NotNull(message = "sessionEnd 不能为空")
        LocalDateTime sessionEnd,

        LocalDateTime preheatAt,

        @NotEmpty(message = "items 不能为空")
        @Valid
        List<Item> items
) {

    public record Item(
            @NotNull(message = "shopId 不能为空")
            @Min(value = 1, message = "shopId 必须是正整数")
            Long shopId,

            @NotNull(message = "spuId 不能为空")
            @Min(value = 1, message = "spuId 必须是正整数")
            Long spuId,

            @NotNull(message = "skuId 不能为空")
            @Min(value = 1, message = "skuId 必须是正整数")
            Long skuId,

            @NotNull(message = "seckillPrice 不能为空")
            @Min(value = 1, message = "seckillPrice 必须大于 0")
            Long seckillPrice,

            @NotNull(message = "seckillStock 不能为空")
            @Min(value = 1, message = "seckillStock 必须大于 0")
            Integer seckillStock,

            @NotNull(message = "limitPerUser 不能为空")
            @Min(value = 1, message = "limitPerUser 必须大于等于 1")
            Integer limitPerUser
    ) {
    }

    public SeckillActivitySaveCommand toCommand() {
        List<SeckillActivitySaveCommand.ItemCommand> itemCommands = items == null
                ? List.of()
                : items.stream()
                        .map(item -> new SeckillActivitySaveCommand.ItemCommand(
                                item.shopId(), item.spuId(), item.skuId(),
                                item.seckillPrice(), item.seckillStock(), item.limitPerUser()))
                        .toList();
        return new SeckillActivitySaveCommand(
                activityName, sessionStart, sessionEnd, preheatAt, itemCommands);
    }
}
