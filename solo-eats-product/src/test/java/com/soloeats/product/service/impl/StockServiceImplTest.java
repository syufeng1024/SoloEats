package com.soloeats.product.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.domain.entity.StockLock;
import com.soloeats.product.domain.entity.StockLog;
import com.soloeats.product.domain.enums.StockLockStatus;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.mapper.StockLockMapper;
import com.soloeats.product.mapper.StockLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    private static final long SHOP_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long ORDER_ID = 90001L;

    @Mock
    private SkuStockMapper skuStockMapper;

    @Mock
    private StockLockMapper stockLockMapper;

    @Mock
    private StockLogMapper stockLogMapper;

    @InjectMocks
    private StockServiceImpl stockService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stockService, "lockMinutes", 15L);
    }

    @Test
    @DisplayName("锁定库存成功：写锁定记录与 LOCK 流水，expire_at 按配置分钟数顺延")
    void lockSuccessWritesLockAndLog() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 2)).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(98, 2, 0));

        LocalDateTime beforeCall = LocalDateTime.now();
        stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 2);

        ArgumentCaptor<StockLock> lockCaptor = ArgumentCaptor.forClass(StockLock.class);
        verify(stockLockMapper).insert(lockCaptor.capture());
        StockLock savedLock = lockCaptor.getValue();
        assertThat(savedLock.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(savedLock.getSkuId()).isEqualTo(SKU_ID);
        assertThat(savedLock.getQty()).isEqualTo(2);
        assertThat(savedLock.getStatus()).isEqualTo(StockLockStatus.LOCKED.getCode());
        assertThat(savedLock.getExpireAt()).isAfter(beforeCall.plusMinutes(14));

        StockLog log = captureLog();
        assertThat(log.getBizType()).isEqualTo("ORDER");
        assertThat(log.getBizId()).isEqualTo(ORDER_ID);
        assertThat(log.getOp()).isEqualTo("LOCK");
        assertThat(log.getQty()).isEqualTo(2);
        assertThat(log.getBeforeAvailable()).isEqualTo(100);
        assertThat(log.getAfterAvailable()).isEqualTo(98);
    }

    @Test
    @DisplayName("库存不足时抛 STOCK_NOT_ENOUGH，且不写锁定记录与流水")
    void lockInsufficientStockLeavesNoTrace() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 5))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH.getCode());

        verify(stockLockMapper, never()).insert(any(StockLock.class));
        verify(stockLogMapper, never()).insert(any(StockLog.class));
    }

    @Test
    @DisplayName("锁定成功但库存行读取不到时抛 STOCK_STATE_INVALID")
    void lockWithoutStockRowFails() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 1)).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());
    }

    @Test
    @DisplayName("确认库存：条件更新 + 锁定记录 0->1")
    void confirmSuccess() {
        when(skuStockMapper.confirmStock(SHOP_ID, SKU_ID, 2)).thenReturn(1);
        when(stockLockMapper.updateStatus(ORDER_ID, SKU_ID,
                StockLockStatus.LOCKED.getCode(), StockLockStatus.CONFIRMED.getCode())).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(98, 0, 2));

        stockService.confirm(ORDER_ID, SHOP_ID, SKU_ID, 2);

        StockLog log = captureLog();
        assertThat(log.getOp()).isEqualTo("CONFIRM");
        verify(stockLockMapper).updateStatus(ORDER_ID, SKU_ID,
                StockLockStatus.LOCKED.getCode(), StockLockStatus.CONFIRMED.getCode());
    }

    @Test
    @DisplayName("确认库存条件更新失败时抛 STOCK_STATE_INVALID，且不改锁状态")
    void confirmFailsWhenConditionNotMet() {
        when(skuStockMapper.confirmStock(SHOP_ID, SKU_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> stockService.confirm(ORDER_ID, SHOP_ID, SKU_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());

        verify(stockLockMapper, never()).updateStatus(anyLong(), anyLong(), anyInt(), anyInt());
        verify(stockLogMapper, never()).insert(any(StockLog.class));
    }

    @Test
    @DisplayName("释放库存：available 回补，锁定记录 0->2，流水 before = after - qty")
    void releaseSuccess() {
        when(skuStockMapper.releaseStock(SHOP_ID, SKU_ID, 2)).thenReturn(1);
        when(stockLockMapper.updateStatus(ORDER_ID, SKU_ID,
                StockLockStatus.LOCKED.getCode(), StockLockStatus.RELEASED.getCode())).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(100, 0, 0));

        stockService.release(ORDER_ID, SHOP_ID, SKU_ID, 2);

        StockLog log = captureLog();
        assertThat(log.getOp()).isEqualTo("RELEASE");
        assertThat(log.getBeforeAvailable()).isEqualTo(98);
        assertThat(log.getAfterAvailable()).isEqualTo(100);
    }

    @Test
    @DisplayName("退款回补库存：锁定记录 1->3，流水 before = after - qty")
    void refundSuccess() {
        when(skuStockMapper.refundStock(SHOP_ID, SKU_ID, 2)).thenReturn(1);
        when(stockLockMapper.updateStatus(ORDER_ID, SKU_ID,
                StockLockStatus.CONFIRMED.getCode(), StockLockStatus.REFUNDED.getCode())).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(100, 0, 0));

        stockService.refund(ORDER_ID, SHOP_ID, SKU_ID, 2);

        StockLog log = captureLog();
        assertThat(log.getBizType()).isEqualTo("REFUND");
        assertThat(log.getOp()).isEqualTo("REFUND");
        assertThat(log.getBeforeAvailable()).isEqualTo(98);
        verify(stockLockMapper).updateStatus(ORDER_ID, SKU_ID,
                StockLockStatus.CONFIRMED.getCode(), StockLockStatus.REFUNDED.getCode());
    }

    @Test
    @DisplayName("退款时 sold_qty 不足抛 STOCK_STATE_INVALID")
    void refundFailsWhenSoldQtyNotEnough() {
        when(skuStockMapper.refundStock(SHOP_ID, SKU_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> stockService.refund(ORDER_ID, SHOP_ID, SKU_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());

        verify(stockLogMapper, never()).insert(any(StockLog.class));
    }

    private StockLog captureLog() {
        ArgumentCaptor<StockLog> captor = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    private SkuStock stock(int available, int locked, int sold) {
        SkuStock stock = new SkuStock();
        stock.setShopId(SHOP_ID);
        stock.setSkuId(SKU_ID);
        stock.setAvailableQty(available);
        stock.setLockedQty(locked);
        stock.setSoldQty(sold);
        return stock;
    }
}
