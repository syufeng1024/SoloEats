package com.soloeats.product.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.domain.entity.StockLock;
import com.soloeats.product.domain.entity.StockLog;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存防超卖符合性用例。需求来源：规格书 9.1「无超卖」、11.2「下单」、13 章「并发扣库存零缺陷」。
 *
 * <p>断言要点：
 * <ol>
 *   <li>扣减必须是"按增量扣减 + {@code available_qty >= qty} 条件"，不允许"先查再赋值"——
 *       故用例显式校验传给 Mapper 的是请求量（delta）而非绝对值；</li>
 *   <li>条件更新影响 0 行即视为库存不足，立即抛 {@code STOCK_NOT_ENOUGH}，
 *       且不得留下锁定记录与流水（否则会出现"锁了但没扣"的幽灵锁）；</li>
 *   <li>库存只够一单时，第二单必须失败，不得产生第二条锁定记录（不超卖）；</li>
 *   <li>确认/释放/退款各自的条件更新失败都必须抛 {@code STOCK_STATE_INVALID}，
 *       防止"未扣减却确认"（少卖）或"重复释放"（库存虚增，等价于超卖）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StockOversellComplianceTest {

    private static final long SHOP_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long ORDER_ID = 90001L;
    private static final long OTHER_ORDER_ID = 90002L;

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
    @DisplayName("条件更新影响 0 行即抛 STOCK_NOT_ENOUGH，不留锁定记录与流水")
    void lockRejectedWhenConditionalUpdateAffectsNoRow() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 5))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH.getCode());

        verify(stockLockMapper, never()).insert(any(StockLock.class));
        verify(stockLogMapper, never()).insert(any(StockLog.class));
    }

    @Test
    @DisplayName("锁定按增量传参（3 件传 3，不是传剩余量），流水前后可用量差 = 扣减量")
    void lockPassesDeltaNotAbsoluteQuantity() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 3)).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(7, 3, 0));

        stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 3);

        // 精确参数校验：若有人把 qty 改成"剩余可用量"或"0"，此断言立即失败
        verify(skuStockMapper).lockStock(SHOP_ID, SKU_ID, 3);

        ArgumentCaptor<StockLog> captor = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        StockLog log = captor.getValue();
        assertThat(log.getBeforeAvailable() - log.getAfterAvailable()).isEqualTo(3);
        assertThat(log.getAfterAvailable()).isEqualTo(7);
    }

    @Test
    @DisplayName("库存只够一单：第二单失败，只落一条锁定记录（不超卖）")
    void sequentialOrdersCannotOversell() {
        when(skuStockMapper.lockStock(SHOP_ID, SKU_ID, 5)).thenReturn(1, 0);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(0, 5, 0));

        stockService.lock(ORDER_ID, SHOP_ID, SKU_ID, 5);

        assertThatThrownBy(() -> stockService.lock(OTHER_ORDER_ID, SHOP_ID, SKU_ID, 5))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH.getCode());

        verify(stockLockMapper, times(1)).insert(any(StockLock.class));
    }

    @Test
    @DisplayName("确认时锁定数量不足：抛 STOCK_STATE_INVALID，且不改锁定记录状态")
    void confirmRejectedWhenLockedQtyInsufficient() {
        when(skuStockMapper.confirmStock(SHOP_ID, SKU_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> stockService.confirm(ORDER_ID, SHOP_ID, SKU_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());

        verify(stockLockMapper, never()).updateStatus(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("重复释放同一订单：第二次条件更新失败，抛错且不二次增加可用库存")
    void releaseCannotInflateStockTwice() {
        when(skuStockMapper.releaseStock(SHOP_ID, SKU_ID, 2)).thenReturn(1, 0);
        when(stockLockMapper.updateStatus(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(1);
        when(skuStockMapper.selectOne(any())).thenReturn(stock(2, 0, 0));

        stockService.release(ORDER_ID, SHOP_ID, SKU_ID, 2);

        assertThatThrownBy(() -> stockService.release(ORDER_ID, SHOP_ID, SKU_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());

        // 只成功回补一次，避免库存虚增
        verify(skuStockMapper, times(2)).releaseStock(SHOP_ID, SKU_ID, 2);
        verify(stockLogMapper, times(1)).insert(any(StockLog.class));
    }

    @Test
    @DisplayName("退款回补要求已售数量足够：不足时抛 STOCK_STATE_INVALID")
    void refundRejectedWhenSoldQtyInsufficient() {
        when(skuStockMapper.refundStock(SHOP_ID, SKU_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> stockService.refund(ORDER_ID, SHOP_ID, SKU_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.STOCK_STATE_INVALID.getCode());

        verify(stockLockMapper, never()).updateStatus(anyLong(), anyLong(), anyInt(), anyInt());
    }

    private SkuStock stock(int availableQty, int lockedQty, int soldQty) {
        SkuStock stock = new SkuStock();
        stock.setId(1L);
        stock.setShopId(SHOP_ID);
        stock.setSkuId(SKU_ID);
        stock.setAvailableQty(availableQty);
        stock.setLockedQty(lockedQty);
        stock.setSoldQty(soldQty);
        stock.setSoldOut(false);
        return stock;
    }
}
