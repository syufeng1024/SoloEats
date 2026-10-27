package com.soloeats.order.service.view;

import java.time.LocalDateTime;

/**
 * 平台异常订单池读模型，数据源 t_order.exception_type != 0。
 */
public record AdminExceptionOrderView(
        Long orderId,
        String orderNo,
        Long merchantId,
        Long shopId,
        Integer status,
        String statusName,
        Integer exceptionType,
        String exceptionTypeName,
        Long payAmount,
        LocalDateTime createdAt
) {
}
