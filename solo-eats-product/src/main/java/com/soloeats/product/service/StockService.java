package com.soloeats.product.service;

/**
 * SKU 库存操作。调用方应保证同一订单的多个 SKU 操作在同一个本地事务内。
 */
public interface StockService {

    /**
     * 提交订单时锁定库存：available_qty -> locked_qty。
     */
    void lock(Long orderId, Long shopId, Long skuId, int qty);

    /**
     * 支付成功时确认库存：locked_qty -> sold_qty。
     */
    void confirm(Long orderId, Long shopId, Long skuId, int qty);

    /**
     * 取消/超时时释放锁定：locked_qty -> available_qty。
     */
    void release(Long orderId, Long shopId, Long skuId, int qty);

    /**
     * 售后退款回补：sold_qty -> available_qty，仅可回补商品调用。
     */
    void refund(Long orderId, Long shopId, Long skuId, int qty);
}
