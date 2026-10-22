package com.soloeats.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.product.mapper.MenuAddonRow;
import com.soloeats.product.mapper.MenuRow;
import com.soloeats.product.mapper.ShopMenuMapper;
import com.soloeats.product.service.view.ShopMenuView;
import com.soloeats.product.support.ProductProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopMenuServiceImplTest {

    private final ShopMenuMapper shopMenuMapper = mock(ShopMenuMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final ShopMenuServiceImpl service = new ShopMenuServiceImpl(
            shopMenuMapper, redisTemplate, new ProductProperties(), new ObjectMapper());

    @Test
    @DisplayName("菜单按分类聚合，SKU 未全部估清时商品可售")
    void menuAggregatesCategorySpuSkuAndAddon() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(shopMenuMapper.selectMenuRows(1L)).thenReturn(List.of(
                row(1001L, "中杯", 1200L, false),
                row(1002L, "大杯", 1500L, true)
        ));
        when(shopMenuMapper.selectAddonRows(anyList())).thenReturn(List.of(addonRow()));

        List<ShopMenuView> menu = service.menu(1L);

        assertThat(menu).hasSize(1);
        assertThat(menu.get(0).categoryName()).isEqualTo("饮品");
        ShopMenuView.MenuItemView item = menu.get(0).items().get(0);
        assertThat(item.spuId()).isEqualTo(2L);
        assertThat(item.minPrice()).isEqualTo(1200L);
        assertThat(item.soldOut()).isFalse();
        assertThat(item.skus()).hasSize(2);
        assertThat(item.addonGroups()).hasSize(1);
        assertThat(item.addonGroups().get(0).items()).hasSize(1);
    }

    @Test
    @DisplayName("门店商品估清时整项 soldOut=true")
    void shopSpuSoldOutMarksWholeItemSoldOut() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        MenuRow soldOutRow = row(1001L, "中杯", 1200L, false);
        soldOutRow.setShopSpuStatus(2);
        when(shopMenuMapper.selectMenuRows(1L)).thenReturn(List.of(soldOutRow));
        when(shopMenuMapper.selectAddonRows(anyList())).thenReturn(List.of());

        ShopMenuView.MenuItemView item = service.menu(1L).get(0).items().get(0);

        assertThat(item.soldOut()).isTrue();
        assertThat(item.skus().get(0).soldOut()).isTrue();
    }

    private MenuRow row(Long skuId, String skuName, Long price, boolean soldOut) {
        MenuRow row = new MenuRow();
        row.setCategoryId(3L);
        row.setCategoryName("饮品");
        row.setCategorySort(2);
        row.setShopSpuId(11L);
        row.setSpuId(2L);
        row.setShopSpuStatus(0);
        row.setSpuSort(90);
        row.setSpuName("冰柠檬茶");
        row.setMainImage("/api/v1/files/1/raw");
        row.setTagsJson("[\"招牌\"]");
        row.setPackagingFee(0L);
        row.setMinPurchaseQty(1);
        row.setLimitPerOrder(0);
        row.setMonthSales(10);
        row.setSkuId(skuId);
        row.setSkuName(skuName);
        row.setSpecJson("{\"杯型\":\"" + skuName + "\"}");
        row.setPrice(price);
        row.setAvailableQty(soldOut ? 0 : 20);
        row.setSoldOut(soldOut);
        row.setSkuSort(1);
        return row;
    }

    private MenuAddonRow addonRow() {
        MenuAddonRow row = new MenuAddonRow();
        row.setSpuId(2L);
        row.setGroupId(1L);
        row.setGroupName("加料");
        row.setMinSelect(0);
        row.setMaxSelect(3);
        row.setGroupSort(1);
        row.setItemId(1L);
        row.setItemName("珍珠");
        row.setItemPrice(200L);
        row.setItemSort(1);
        return row;
    }
}
