package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.domain.entity.StockLock;
import com.soloeats.product.domain.entity.StockLog;
import com.soloeats.product.domain.enums.StockLockStatus;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.mapper.StockLockMapper;
import com.soloeats.product.mapper.StockLogMapper;
import com.soloeats.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final SkuStockMapper skuStockMapper;
    private final StockLockMapper stockLockMapper;
    private final StockLogMapper stockLogMapper;

    @Value("${app.order.pay-timeout-minutes:15}")
    private long lockMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long orderId, Long shopId, Long skuId, int qty) {
        if (skuStockMapper.lockStock(shopId, skuId, qty) != 1) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        StockLock lock = new StockLock();
        lock.setOrderId(orderId);
        lock.setShopId(shopId);
        lock.setSkuId(skuId);
        lock.setQty(qty);
        lock.setStatus(StockLockStatus.LOCKED.getCode());
        lock.setExpireAt(LocalDateTime.now().plusMinutes(lockMinutes));
        stockLockMapper.insert(lock);

        SkuStock stock = currentStock(shopId, skuId);
        writeLog(shopId, skuId, "ORDER", orderId, "LOCK", qty,
                stock.getAvailableQty() + qty, stock.getAvailableQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, Long shopId, Long skuId, int qty) {
        if (skuStockMapper.confirmStock(shopId, skuId, qty) != 1) {
            throw new BizException(ErrorCode.STOCK_STATE_INVALID);
        }
        updateLockStatus(orderId, skuId, StockLockStatus.LOCKED, StockLockStatus.CONFIRMED);

        SkuStock stock = currentStock(shopId, skuId);
        writeLog(shopId, skuId, "ORDER", orderId, "CONFIRM", qty,
                stock.getAvailableQty(), stock.getAvailableQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long orderId, Long shopId, Long skuId, int qty) {
        if (skuStockMapper.releaseStock(shopId, skuId, qty) != 1) {
            throw new BizException(ErrorCode.STOCK_STATE_INVALID);
        }
        updateLockStatus(orderId, skuId, StockLockStatus.LOCKED, StockLockStatus.RELEASED);

        SkuStock stock = currentStock(shopId, skuId);
        writeLog(shopId, skuId, "ORDER", orderId, "RELEASE", qty,
                stock.getAvailableQty() - qty, stock.getAvailableQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId, Long shopId, Long skuId, int qty) {
        if (skuStockMapper.refundStock(shopId, skuId, qty) != 1) {
            throw new BizException(ErrorCode.STOCK_STATE_INVALID);
        }
        updateLockStatus(orderId, skuId, StockLockStatus.CONFIRMED, StockLockStatus.REFUNDED);

        SkuStock stock = currentStock(shopId, skuId);
        writeLog(shopId, skuId, "REFUND", orderId, "REFUND", qty,
                stock.getAvailableQty() - qty, stock.getAvailableQty());
    }

    private SkuStock currentStock(Long shopId, Long skuId) {
        SkuStock stock = skuStockMapper.selectOne(new LambdaQueryWrapper<SkuStock>()
                .eq(SkuStock::getShopId, shopId)
                .eq(SkuStock::getSkuId, skuId));
        if (stock == null) {
            throw new BizException(ErrorCode.STOCK_STATE_INVALID);
        }
        return stock;
    }

    private void writeLog(Long shopId, Long skuId, String bizType, Long bizId,
                          String op, int qty, int beforeAvailable, int afterAvailable) {
        StockLog log = new StockLog();
        log.setShopId(shopId);
        log.setSkuId(skuId);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setOp(op);
        log.setQty(qty);
        log.setBeforeAvailable(beforeAvailable);
        log.setAfterAvailable(afterAvailable);
        stockLogMapper.insert(log);
    }

    private void updateLockStatus(Long orderId, Long skuId, StockLockStatus from, StockLockStatus to) {
        if (stockLockMapper.updateStatus(orderId, skuId, from.getCode(), to.getCode()) != 1) {
            throw new BizException(ErrorCode.STOCK_STATE_INVALID);
        }
    }
}
