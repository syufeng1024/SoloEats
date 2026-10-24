package com.soloeats.app.support;

import com.soloeats.delivery.service.OrderExceptionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * delivery 模块不直接依赖 order 实体与 mapper，由 app 模块把异常上报
 * 写入既有 t_order_exception，驱动平台异常订单池。
 */
@Component
@RequiredArgsConstructor
public class OrderExceptionPortAdapter implements OrderExceptionPort {

    private final OrderExceptionMapper orderExceptionMapper;

    @Override
    public void record(Long orderId, Long deliveryOrderId, Integer exceptionType,
                       Integer sourceType, Long sourceId, String description) {
        if (orderId == null) {
            return;
        }
        orderExceptionMapper.insert(orderId, deliveryOrderId, exceptionType,
                sourceType, sourceId, description);
    }
}
