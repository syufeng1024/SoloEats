package com.soloeats.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.ShopSpu;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.domain.entity.SoldOutRecord;
import com.soloeats.product.mapper.ShopSpuMapper;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.mapper.SoldOutRecordMapper;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.command.SoldOutCommand;
import com.soloeats.store.service.ShopService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoldOutServiceImplTest {

    private final SkuMapper skuMapper = mock(SkuMapper.class);
    private final SkuStockMapper skuStockMapper = mock(SkuStockMapper.class);
    private final ShopSpuMapper shopSpuMapper = mock(ShopSpuMapper.class);
    private final SoldOutRecordMapper soldOutRecordMapper = mock(SoldOutRecordMapper.class);
    private final ShopService shopService = mock(ShopService.class);
    private final ShopMenuService shopMenuService = mock(ShopMenuService.class);

    private final SoldOutServiceImpl service = new SoldOutServiceImpl(
            skuMapper, skuStockMapper, shopSpuMapper, soldOutRecordMapper,
            shopService, shopMenuService);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Sku.class);
        TableInfoHelper.initTableInfo(assistant, SkuStock.class);
        TableInfoHelper.initTableInfo(assistant, ShopSpu.class);
        TableInfoHelper.initTableInfo(assistant, SoldOutRecord.class);
    }

    @Test
    @DisplayName("估清同时更新库存标记、写估清记录、置门店商品为估清")
    void markSoldOutKeepsThreeWayConsistency() {
        Sku sku = sku(1001L, 1L, 1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku));
        when(skuStockMapper.selectOne(any())).thenReturn(stock(1001L, false));
        when(skuStockMapper.updateManualStock(anyLong(), anyLong(), isNull(), any())).thenReturn(1);
        when(shopSpuMapper.update(isNull(), any())).thenReturn(1);

        service.markSoldOut(9L,
                new SoldOutCommand(1L, List.of(1001L), "牛肉售完"), 7L, "店长");

        verify(skuStockMapper).updateManualStock(1L, 1001L, null, true);
        verify(soldOutRecordMapper).insert(any(SoldOutRecord.class));
        verify(shopSpuMapper).update(isNull(), any());
        verify(shopMenuService).evictMenuCache(1L);
    }

    @Test
    @DisplayName("重复估清不重复写记录")
    void markSoldOutTwiceWritesSingleRecord() {
        Sku sku = sku(1001L, 1L, 1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku));
        when(skuStockMapper.selectOne(any())).thenReturn(stock(1001L, true));
        when(shopSpuMapper.update(isNull(), any())).thenReturn(1);

        service.markSoldOut(9L,
                new SoldOutCommand(1L, List.of(1001L), "牛肉售完"), 7L, "店长");

        verify(soldOutRecordMapper, never()).insert(any(SoldOutRecord.class));
    }

    @Test
    @DisplayName("恢复库存时关闭估清记录并在无估清 SKU 时恢复门店商品")
    void restoreReopensSoldOutState() {
        Sku sku = sku(1001L, 1L, 1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku));
        when(skuStockMapper.selectOne(any())).thenReturn(stock(1001L, true));
        when(skuStockMapper.selectCount(any())).thenReturn(0L);
        when(shopSpuMapper.update(isNull(), any())).thenReturn(1);

        service.restore(9L, new SoldOutCommand(1L, List.of(1001L), null), 7L, "店长");

        verify(skuStockMapper).updateManualStock(1L, 1001L, null, false);
        verify(soldOutRecordMapper).markRestored(1L, 1001L);
        verify(shopSpuMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("估清原因不能为空")
    void rejectEmptyReason() {
        assertThatThrownBy(() -> service.markSoldOut(9L,
                new SoldOutCommand(1L, List.of(1001L), "  "), 7L, "店长"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        verify(skuStockMapper, never()).updateManualStock(anyLong(), anyLong(), any(), any());
    }

    private Sku sku(Long id, Long shopId, Long spuId) {
        Sku sku = new Sku();
        sku.setId(id);
        sku.setShopId(shopId);
        sku.setSpuId(spuId);
        sku.setSkuName("招牌牛肉面");
        sku.setPrice(2800L);
        sku.setStatus(0);
        return sku;
    }

    private SkuStock stock(Long skuId, boolean soldOut) {
        SkuStock stock = new SkuStock();
        stock.setShopId(1L);
        stock.setSkuId(skuId);
        stock.setAvailableQty(10);
        stock.setLockedQty(0);
        stock.setSoldQty(0);
        stock.setSoldOut(soldOut);
        return stock;
    }
}
