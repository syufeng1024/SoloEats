package com.soloeats.delivery.service;

/**
 * 异常订单池（t_order_exception）写入端口。
 *
 * <p>该表由订单域维护，delivery 模块不直接依赖 order 的实体与 mapper；
 * 由 app 模块的适配器实现，同事务内落库，驱动平台异常订单池。
 */
public interface OrderExceptionPort {

    /**
     * @param orderId           订单 ID
     * @param deliveryOrderId   配送单 ID
     * @param exceptionType     1无骑手 2联系不上 3地址异常 4餐品破损 5商家超时 6自取超时 7其他
     * @param sourceType        1用户 2商家 3骑手 4系统 5平台
     * @param sourceId          上报来源主体 ID（骑手上报时为 riderId）
     * @param description       异常描述
     */
    void record(Long orderId, Long deliveryOrderId, Integer exceptionType,
                Integer sourceType, Long sourceId, String description);
}
