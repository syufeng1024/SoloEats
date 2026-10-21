package com.soloeats.app.dto;

import jakarta.validation.constraints.Min;

public record UpdateSkuStockRequest(
        @Min(value = 0, message = "availableQty 不能为负数")
        Integer availableQty,

        Boolean soldOut
) {
}
