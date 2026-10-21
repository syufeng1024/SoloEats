package com.soloeats.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.domain.entity.SkuStock;
import com.soloeats.product.mapper.SkuMapper;
import com.soloeats.product.mapper.SkuStockMapper;
import com.soloeats.product.service.command.CreateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuStockCommand;
import com.soloeats.product.service.view.MerchantSkuView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantSkuServiceImplTest {

    private final SkuMapper skuMapper = mock(SkuMapper.class);
    private final SkuStockMapper skuStockMapper = mock(SkuStockMapper.class);
    private final MerchantSkuServiceImpl service =
            new MerchantSkuServiceImpl(skuMapper, skuStockMapper, new ObjectMapper());

    @Test
    @DisplayName("创建 SKU 同时写入库存初始值")
    void createSkuWithInitialStock() {
        doAnswer(invocation -> {
            Sku sku = invocation.getArgument(0);
            sku.setId(5001L);
            return 1;
        }).when(skuMapper).insert(any(Sku.class));

        MerchantSkuView view = service.create(1L, new CreateSkuCommand(
                9L, "  招牌牛肉面  ", null, null, 2800L, 0, 10
        ));

        assertThat(view.skuId()).isEqualTo(5001L);
        assertThat(view.shopId()).isEqualTo(1L);
        assertThat(view.skuName()).isEqualTo("招牌牛肉面");
        assertThat(view.price()).isEqualTo(2800L);
        assertThat(view.availableQty()).isEqualTo(10);
        assertThat(view.soldOut()).isFalse();
    }

    @Test
    @DisplayName("价格必须大于 0 分")
    void createRejectsNonPositivePrice() {
        assertBizCode(() -> service.create(1L, new CreateSkuCommand(
                9L, "牛肉面", null, null, 0L, 0, 0
        )), ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("不能修改其他门店的 SKU")
    void updateRejectsCrossShopSku() {
        Sku otherShopSku = sku(2L, 1001L);
        when(skuMapper.selectById(1001L)).thenReturn(otherShopSku);

        assertBizCode(() -> service.update(1L, 1001L,
                new UpdateSkuCommand("改价", null, null, 3000L, null)),
                ErrorCode.SKU_NOT_FOUND);
    }

    @Test
    @DisplayName("库存不能设为负数")
    void updateStockRejectsNegativeQuantity() {
        when(skuMapper.selectById(1001L)).thenReturn(sku(1L, 1001L));

        assertBizCode(() -> service.updateStock(1L, 1001L,
                new UpdateSkuStockCommand(-1, null)),
                ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("库存更新后返回最新可售数量与估清标记")
    void updateStockReturnsLatestState() {
        Sku sku = sku(1L, 1001L);
        SkuStock before = stock(1001L, 20, false);
        SkuStock after = stock(1001L, 5, true);
        when(skuMapper.selectById(1001L)).thenReturn(sku);
        when(skuStockMapper.selectOne(any())).thenReturn(before, after);
        when(skuStockMapper.updateManualStock(1L, 1001L, 5, true)).thenReturn(1);

        MerchantSkuView view = service.updateStock(1L, 1001L,
                new UpdateSkuStockCommand(5, true));

        assertThat(view.availableQty()).isEqualTo(5);
        assertThat(view.soldOut()).isTrue();
    }

    private Sku sku(Long shopId, Long skuId) {
        Sku sku = new Sku();
        sku.setId(skuId);
        sku.setShopId(shopId);
        sku.setSpuId(1L);
        sku.setSkuName("招牌牛肉面");
        sku.setPrice(2800L);
        sku.setStatus(0);
        return sku;
    }

    private SkuStock stock(Long skuId, int available, boolean soldOut) {
        SkuStock stock = new SkuStock();
        stock.setId(1L);
        stock.setShopId(1L);
        stock.setSkuId(skuId);
        stock.setAvailableQty(available);
        stock.setLockedQty(0);
        stock.setSoldQty(0);
        stock.setSoldOut(soldOut);
        stock.setVersion(0);
        return stock;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
